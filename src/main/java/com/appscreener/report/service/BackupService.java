package com.appscreener.report.service;

import com.appscreener.report.backup.BackupMeta;
import com.appscreener.report.backup.BackupOutcome;
import com.appscreener.report.backup.HistoryArchiveMapper;
import com.appscreener.report.backup.ReportHistorySnapshot;
import com.appscreener.report.config.BackupProperties;
import com.appscreener.report.entity.ReportMessageEntity;
import com.appscreener.report.entity.ReportCategoryEntity;
import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.BackupInfo;
import com.appscreener.report.repository.ReportCategoryRepository;
import com.appscreener.report.repository.ReportMessageRepository;
import com.appscreener.report.repository.TestRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final Pattern SAFE_ARCHIVE_NAME =
            Pattern.compile("^history-(\\d{8}-\\d{6}|upload-\\d{8}-\\d{6})\\.json\\.gz$");
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final BackupProperties properties;
    private final ReportCategoryRepository categoryRepository;
    private final TestRunRepository testRunRepository;
    private final ReportMessageRepository messageRepository;
    private final TransactionTemplate transactionTemplate;

    public BackupService(BackupProperties properties,
                         ReportCategoryRepository categoryRepository,
                         TestRunRepository testRunRepository,
                         ReportMessageRepository messageRepository,
                         PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.categoryRepository = categoryRepository;
        this.testRunRepository = testRunRepository;
        this.messageRepository = messageRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Path backupDirectory() {
        return Paths.get(properties.getDirectory()).toAbsolutePath().normalize();
    }

    public BackupOutcome createBackup(boolean manual) throws IOException {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Резервное копирование отключено (report.backup.enabled=false)");
        }

        DataFingerprint current = fingerprint();

        if (!manual) {
            if (current.runCount() == 0) {
                log.debug("Scheduled backup skipped: no report history yet");
                return BackupOutcome.skipped("Нет данных для архивации");
            }
            if (properties.isSkipIfUnchanged()) {
                Optional<BackupMeta> last = latestMeta();
                if (last.isPresent() && last.get().isSameAs(
                        current.runCount(), current.messageCount(), current.latestActivity())) {
                    log.info("Scheduled backup skipped: history unchanged ({} runs, {} messages)",
                            current.runCount(), current.messageCount());
                    return BackupOutcome.skipped("История не изменилась с прошлого архива");
                }
            }
        }

        Path dir = backupDirectory();
        Files.createDirectories(dir);

        String baseName = "history-" + FILE_STAMP.format(Instant.now());
        String archiveName = baseName + ".json.gz";
        Path archivePath = dir.resolve(archiveName);

        ReportHistorySnapshot snapshot = buildSnapshot();
        writeGzipJson(archivePath, snapshot);

        BackupMeta meta = new BackupMeta();
        meta.setArchiveFileName(archiveName);
        meta.setCreatedAt(Instant.now());
        meta.setRunCount(current.runCount());
        meta.setMessageCount(current.messageCount());
        meta.setLatestActivity(current.latestActivity());
        MAPPER.writeValue(dir.resolve(baseName + ".meta.json").toFile(), meta);

        pruneOldBackups();

        BackupInfo info = toInfo(archivePath, meta, manual);
        log.info("History archive created: {} ({} KB, {} runs, {} messages)",
                archiveName, info.getSizeBytes() / 1024, meta.getRunCount(), meta.getMessageCount());
        return BackupOutcome.created(info);
    }

    /**
     * Загрузка архива с диска (.json.gz или .zip с history*.json(.gz) внутри).
     * Файл сохраняется в каталог backups; опционально сразу накатывается в БД.
     */
    public BackupInfo uploadArchive(InputStream input, String originalFileName, boolean restoreAfter)
            throws IOException {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Резервное копирование отключено (report.backup.enabled=false)");
        }
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла не указано");
        }
        String safeName = Paths.get(originalFileName).getFileName().toString();
        ReportHistorySnapshot snapshot = parseUploadedArchive(input, safeName);
        validateSnapshot(snapshot);
        if (restoreAfter) {
            assertArchiveHasData(safeName, snapshot);
        }

        Path dir = backupDirectory();
        Files.createDirectories(dir);

        String baseName = "history-upload-" + FILE_STAMP.format(Instant.now());
        String archiveName = baseName + ".json.gz";
        Path archivePath = dir.resolve(archiveName);
        writeGzipJson(archivePath, snapshot);

        BackupMeta meta = metaFromSnapshot(snapshot, archiveName);
        MAPPER.writeValue(dir.resolve(baseName + ".meta.json").toFile(), meta);

        pruneOldBackups();
        log.info("History archive uploaded: {} ({} runs, {} messages), restore={}",
                archiveName, meta.getRunCount(), meta.getMessageCount(), restoreAfter);

        if (restoreAfter) {
            restoreBackup(archiveName);
        }

        return toInfo(archivePath, meta, true);
    }

    public void restoreBackup(String fileName) throws IOException {
        Path archive = resolveBackupFile(fileName)
                .orElseThrow(() -> new IllegalArgumentException("Архив не найден: " + fileName));

        ReportHistorySnapshot snapshot = readGzipJson(archive);
        validateSnapshot(snapshot);
        assertArchiveHasData(fileName, snapshot);

        if (fingerprint().runCount() > 0) {
            try {
                BackupOutcome safety = createBackup(true);
                if (!safety.isSkipped() && safety.getBackup() != null) {
                    log.info("Pre-restore safety archive: {}", safety.getBackup().getFileName());
                }
            } catch (Exception e) {
                log.warn("Pre-restore archive failed: {}", e.getMessage());
            }
        }

        transactionTemplate.executeWithoutResult(status -> applySnapshot(snapshot));
        log.info("History restored from {}: {} runs, {} messages",
                fileName, snapshot.getRuns().size(), snapshot.getMessages().size());
    }

    public ArchiveStats readArchiveStats(String fileName) throws IOException {
        Path archive = resolveBackupFile(fileName)
                .orElseThrow(() -> new IllegalArgumentException("Архив не найден: " + fileName));
        ReportHistorySnapshot snapshot = readGzipJson(archive);
        validateSnapshot(snapshot);
        return new ArchiveStats(snapshot.getRuns().size(), snapshot.getMessages().size());
    }

    private void assertArchiveHasData(String fileName, ReportHistorySnapshot snapshot) {
        if (snapshot.getRuns().isEmpty()) {
            throw new IllegalArgumentException(
                    "Архив «" + fileName + "» пустой (0 прогонов). Накат отменён — текущая база не изменена. "
                            + "Проверьте файл: после скачивания нужен .json.gz или распакованный .json с данными.");
        }
    }

    private void applySnapshot(ReportHistorySnapshot snapshot) {
        if (snapshot.getFormatVersion() >= 2) {
            categoryRepository.deleteAll();
            categoryRepository.flush();
            for (ReportHistorySnapshot.CategoryRecord categoryRecord : snapshot.getCategories()) {
                categoryRepository.save(HistoryArchiveMapper.toCategoryEntity(categoryRecord));
            }
        }

        messageRepository.deleteAll();
        testRunRepository.deleteAll();
        messageRepository.flush();
        testRunRepository.flush();

        Map<String, TestRunEntity> runsById = new HashMap<>();
        for (ReportHistorySnapshot.RunRecord record : snapshot.getRuns()) {
            TestRunEntity run = HistoryArchiveMapper.toRunEntity(record);
            runsById.put(run.getId(), testRunRepository.save(run));
        }

        for (ReportHistorySnapshot.MessageRecord record : snapshot.getMessages()) {
            TestRunEntity run = runsById.get(record.getTestRunId());
            if (run == null) {
                throw new IllegalStateException("Сообщение ссылается на неизвестный прогон: " + record.getTestRunId());
            }
            messageRepository.save(HistoryArchiveMapper.toMessageEntity(record, run));
        }
    }

    public List<BackupInfo> listBackups() throws IOException {
        Path dir = backupDirectory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BackupInfo> result = new ArrayList<>();
        List<Path> archives;
        try (var stream = Files.list(dir)) {
            archives = stream
                    .filter(p -> SAFE_ARCHIVE_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
        }
        for (Path archive : archives) {
            BackupMeta meta = readMetaForArchive(archive).orElse(null);
            if (meta == null || meta.getRunCount() == 0) {
                meta = metaFromSnapshot(readGzipJson(archive), archive.getFileName().toString());
            }
            result.add(toInfo(archive, meta, false));
        }
        return result;
    }

    public Optional<Path> resolveBackupFile(String fileName) {
        if (fileName == null || !SAFE_ARCHIVE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path resolved = backupDirectory().resolve(fileName).normalize();
        if (!resolved.startsWith(backupDirectory()) || !Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    public void pruneOldBackups() throws IOException {
        List<BackupInfo> all = listBackups();
        int keep = Math.max(1, properties.getKeepCount());
        if (all.size() <= keep) {
            return;
        }
        for (int i = keep; i < all.size(); i++) {
            String name = all.get(i).getFileName();
            Path archive = backupDirectory().resolve(name);
            Files.deleteIfExists(archive);
            String base = name.replace(".json.gz", "");
            Files.deleteIfExists(backupDirectory().resolve(base + ".meta.json"));
            log.info("Removed old history archive: {}", name);
        }
    }

    private ReportHistorySnapshot buildSnapshot() {
        List<TestRunEntity> runs = testRunRepository.findAll();
        List<ReportMessageEntity> messages = messageRepository.findAllWithTestRun();

        ReportHistorySnapshot snapshot = new ReportHistorySnapshot();
        snapshot.setExportedAt(Instant.now());
        for (TestRunEntity run : runs) {
            snapshot.getRuns().add(HistoryArchiveMapper.toRunRecord(run));
        }
        for (ReportMessageEntity message : messages) {
            snapshot.getMessages().add(HistoryArchiveMapper.toMessageRecord(message));
        }
        List<ReportCategoryEntity> categories = categoryRepository.findAllByOrderBySortOrderAscLabelAsc();
        for (ReportCategoryEntity category : categories) {
            snapshot.getCategories().add(HistoryArchiveMapper.toCategoryRecord(category));
        }
        return snapshot;
    }

    private DataFingerprint fingerprint() {
        long runs = testRunRepository.count();
        long messages = messageRepository.count();
        Instant latest = testRunRepository.findAll().stream()
                .map(TestRunEntity::getReceivedAt)
                .max(Instant::compareTo)
                .or(() -> messageRepository.findAll().stream()
                        .map(ReportMessageEntity::getReceivedAt)
                        .max(Instant::compareTo))
                .orElse(null);
        return new DataFingerprint((int) runs, (int) messages, latest);
    }

    private Optional<BackupMeta> latestMeta() throws IOException {
        List<BackupInfo> backups = listBackups();
        if (backups.isEmpty()) {
            return Optional.empty();
        }
        return readMetaForArchive(backupDirectory().resolve(backups.get(0).getFileName()));
    }

    private Optional<BackupMeta> readMetaForArchive(Path archive) {
        String base = archive.getFileName().toString().replace(".json.gz", "");
        Path metaPath = archive.getParent().resolve(base + ".meta.json");
        if (!Files.isRegularFile(metaPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(metaPath.toFile(), BackupMeta.class));
        } catch (IOException e) {
            log.warn("Cannot read backup meta {}: {}", metaPath, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeGzipJson(Path target, ReportHistorySnapshot snapshot) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            MAPPER.writeValue(gzip, snapshot);
        }
    }

    private ReportHistorySnapshot readGzipJson(Path source) throws IOException {
        try (InputStream in = Files.newInputStream(source);
             GZIPInputStream gzip = new GZIPInputStream(in)) {
            return MAPPER.readValue(gzip, ReportHistorySnapshot.class);
        }
    }

    private ReportHistorySnapshot parseUploadedArchive(InputStream input, String fileName) throws IOException {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".json.gz")) {
            try (GZIPInputStream gzip = new GZIPInputStream(input)) {
                return MAPPER.readValue(gzip, ReportHistorySnapshot.class);
            }
        }
        if (lower.endsWith(".json")) {
            return MAPPER.readValue(input, ReportHistorySnapshot.class);
        }
        if (lower.endsWith(".zip")) {
            return readSnapshotFromZip(input);
        }
        throw new IllegalArgumentException(
                "Поддерживаются .json.gz, .json (распакованный) и .zip с history*.json внутри");
    }

    private ReportHistorySnapshot readSnapshotFromZip(InputStream input) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            ReportHistorySnapshot plainJson = null;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".json.gz")) {
                    try (GZIPInputStream gzip = new GZIPInputStream(zip)) {
                        return MAPPER.readValue(gzip, ReportHistorySnapshot.class);
                    }
                }
                if (name.endsWith(".json") && plainJson == null) {
                    plainJson = MAPPER.readValue(zip, ReportHistorySnapshot.class);
                }
            }
            if (plainJson != null) {
                return plainJson;
            }
        }
        throw new IllegalArgumentException("В ZIP не найден файл history*.json или history*.json.gz");
    }

    private void validateSnapshot(ReportHistorySnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Пустой архив");
        }
        if (snapshot.getFormatVersion() < ReportHistorySnapshot.MIN_SUPPORTED_FORMAT_VERSION
                || snapshot.getFormatVersion() > ReportHistorySnapshot.FORMAT_VERSION) {
            throw new IllegalStateException("Неподдерживаемая версия архива: " + snapshot.getFormatVersion());
        }
        if (snapshot.getRuns() == null || snapshot.getMessages() == null) {
            throw new IllegalArgumentException("Некорректная структура архива");
        }
        if (snapshot.getCategories() == null) {
            snapshot.setCategories(List.of());
        }
    }

    private BackupMeta metaFromSnapshot(ReportHistorySnapshot snapshot, String archiveFileName) {
        Instant latest = snapshot.getRuns().stream()
                .map(ReportHistorySnapshot.RunRecord::getReceivedAt)
                .filter(t -> t != null)
                .max(Instant::compareTo)
                .or(() -> snapshot.getMessages().stream()
                        .map(ReportHistorySnapshot.MessageRecord::getReceivedAt)
                        .filter(t -> t != null)
                        .max(Instant::compareTo))
                .orElse(snapshot.getExportedAt());

        BackupMeta meta = new BackupMeta();
        meta.setArchiveFileName(archiveFileName);
        meta.setCreatedAt(Instant.now());
        meta.setRunCount(snapshot.getRuns().size());
        meta.setMessageCount(snapshot.getMessages().size());
        meta.setLatestActivity(latest);
        return meta;
    }

    private BackupInfo toInfo(Path archive, BackupMeta meta, boolean manual) throws IOException {
        BackupInfo info = new BackupInfo();
        info.setFileName(archive.getFileName().toString());
        info.setSizeBytes(Files.size(archive));
        info.setCreatedAt(meta != null && meta.getCreatedAt() != null
                ? meta.getCreatedAt()
                : Files.getLastModifiedTime(archive).toInstant());
        info.setManual(manual);
        if (meta != null) {
            info.setRunCount(meta.getRunCount());
            info.setMessageCount(meta.getMessageCount());
        }
        return info;
    }

    private record DataFingerprint(int runCount, int messageCount, Instant latestActivity) {
    }

    public record ArchiveStats(int runCount, int messageCount) {
    }
}
