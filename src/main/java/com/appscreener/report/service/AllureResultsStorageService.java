package com.appscreener.report.service;

import com.appscreener.report.entity.AllureAttachmentEntity;
import com.appscreener.report.entity.AllureTestResultEntity;
import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.CategoryInfo;
import com.appscreener.report.model.ReportCategory;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.model.allure.AllureAttachmentRef;
import com.appscreener.report.model.allure.AllureParsedResult;
import com.appscreener.report.model.allure.AllureParsedRun;
import com.appscreener.report.model.allure.AllureTestPageView;
import com.appscreener.report.repository.AllureAttachmentRepository;
import com.appscreener.report.repository.AllureTestResultRepository;
import com.appscreener.report.repository.TestRunRepository;
import com.appscreener.report.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Импорт каталога allure-results в прогон.
 * <p>
 * Компактное хранение, не переполняющее память:
 * <ul>
 *   <li>архив распаковывается во временный каталог на диске (не в heap);</li>
 *   <li>полный JSON результата теста хранится сжатым (gzip) в БД — страница теста
 *       воспроизводится только из БД, попадает в бэкапы БД;</li>
 *   <li>аттачи (скриншоты/тексты/видео) складываются в один deflate-пак на прогон
 *       {@code data/allure/{runId}-attachments.zip} и отдаются стримингом, без загрузки
 *       файла целиком в память.</li>
 * </ul>
 */
@Service
public class AllureResultsStorageService {

    private static final Logger log = LoggerFactory.getLogger(AllureResultsStorageService.class);

    private static final int MAX_ZIP_ENTRIES = 50_000;
    private static final long MAX_ZIP_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_ZIP_ENTRY_BYTES = 512L * 1024 * 1024;

    /** Патологические *-result.json больше — обрезаются до базовых полей (без шагов). */
    private static final long RESULT_JSON_RAW_CAP_BYTES = 32L * 1024 * 1024;

    private static final int INLINE_TEXT_CAP_BYTES = 256 * 1024;

    private final AllureResultsParser parser;
    private final TestRunRepository testRunRepository;
    private final AllureTestResultRepository testResultRepository;
    private final AllureAttachmentRepository attachmentRepository;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${report.allure-results.storage-dir:./data/allure}")
    private String storageDir;

    public AllureResultsStorageService(AllureResultsParser parser,
                                       TestRunRepository testRunRepository,
                                       AllureTestResultRepository testResultRepository,
                                       AllureAttachmentRepository attachmentRepository,
                                       CategoryService categoryService) {
        this.parser = parser;
        this.testRunRepository = testRunRepository;
        this.testResultRepository = testResultRepository;
        this.attachmentRepository = attachmentRepository;
        this.categoryService = categoryService;
    }

    public record ImportOutcome(TestRunEntity run, AllureParsedRun parsed) {
    }

    /** Метаданные прогона из параметров загрузки. */
    public record RunMeta(String standUrl, String ciSuites, String pipelineUrl, String title) {

        public static RunMeta empty() {
            return new RunMeta(null, null, null, null);
        }
    }

    /**
     * Импорт zip-архива каталога allure-results (содержимое каталога или сам каталог в корне архива).
     *
     * @param runId    существующий прогон (если null — создаётся новый)
     * @param threadId категория (message_thread_id) для нового прогона
     */
    public ImportOutcome importZip(InputStream zipStream, String runId, String threadId, RunMeta meta) throws IOException {
        Path tempDir = Files.createTempDirectory("allure-import");
        try {
            extractZip(zipStream, tempDir);
            Path resultsDir = resolveResultsRoot(tempDir);
            return importDirectory(resultsDir, runId, threadId, meta);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Transactional
    public ImportOutcome importDirectory(Path resultsDir, String runId, String threadId, RunMeta meta) throws IOException {
        AllureParsedRun parsed = parser.parseDirectory(resultsDir);
        if (parsed.results().isEmpty()) {
            throw new IllegalArgumentException("В архиве нет *-result.json — это не allure-results?");
        }

        TestRunEntity run = resolveRun(runId, threadId);
        replacePreviousImport(run.getId());

        // аттачи → один deflate-пак на прогон; loose-каталогов не оставляем
        Path pack = attachmentPackPath(run.getId());
        writeAttachmentPack(resultsDir, pack);
        deleteRecursive(runDirectory(run.getId()));

        List<AllureTestResultEntity> entities = new ArrayList<>();
        int sortIndex = 0;
        for (AllureParsedResult result : parsed.results()) {
            AllureTestResultEntity entity = new AllureTestResultEntity();
            entity.setId(result.uuid());
            entity.setRunId(run.getId());
            entity.setSortIndex(sortIndex++);
            entity.setName(result.name());
            entity.setFullName(result.fullName());
            entity.setStatus(result.status());
            entity.setStatusMessage(result.message());
            entity.setStartMs(result.start());
            entity.setStopMs(result.stop());
            entity.setSuiteName(result.suiteName());
            entity.setFlaky(result.flaky());
            entity.setResultJsonGz(readResultJsonGz(resultsDir, result));
            entities.add(entity);
        }
        testResultRepository.saveAll(entities);

        List<AllureAttachmentEntity> attachments = new ArrayList<>();
        Map<String, AllureAttachmentRef> refs = parser.attachmentRefs(resultsDir);
        for (Map.Entry<String, Path> entry : parser.attachmentFiles(resultsDir).entrySet()) {
            AllureAttachmentEntity entity = new AllureAttachmentEntity();
            entity.setSource(entry.getKey());
            entity.setRunId(run.getId());
            AllureAttachmentRef ref = refs.get(entry.getKey());
            // тип/имя из JSON-рефа авторитетнее расширения (бывают image/png без расширения)
            entity.setName(ref != null && ref.name() != null ? ref.name() : entry.getKey());
            entity.setContentType(ref != null && ref.type() != null && !ref.type().isBlank()
                    ? ref.type() : guessContentType(entry.getKey()));
            entity.setSizeBytes(Files.size(entry.getValue()));
            attachments.add(entity);
        }
        attachmentRepository.saveAll(attachments);

        applyRunSummary(run, parsed, meta);
        log.info("Импортирован allure-прогон {}: {} тестов, {} аттачей, пак {} байт",
                run.getId(), entities.size(), attachments.size(), Files.exists(pack) ? Files.size(pack) : 0);
        return new ImportOutcome(run, parsed);
    }

    /** gzip полного result JSON; сверх капа — урезанный JSON без шагов. */
    private byte[] readResultJsonGz(Path resultsDir, AllureParsedResult result) throws IOException {
        Path file = resultsDir.resolve(result.uuid() + "-result.json");
        if (!Files.exists(file)) {
            return gzip(minimalResultJson(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (Files.size(file) > RESULT_JSON_RAW_CAP_BYTES) {
            log.warn("result.json {} больше {} байт — шаги не сохраняются",
                    result.uuid(), RESULT_JSON_RAW_CAP_BYTES);
            return gzip(minimalResultJson(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return gzip(Files.readAllBytes(file));
    }

    private String minimalResultJson(AllureParsedResult result) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("uuid", result.uuid());
        if (result.name() != null) {
            root.put("name", result.name());
        }
        if (result.fullName() != null) {
            root.put("fullName", result.fullName());
        }
        root.put("status", result.status());
        ObjectNode details = root.putObject("statusDetails");
        if (result.message() != null) {
            details.put("message", result.message());
        }
        root.putArray("steps");
        root.putArray("attachments");
        root.putArray("parameters");
        ArrayNode labels = root.putArray("labels");
        if (result.suiteName() != null) {
            labels.addObject().put("name", "suite").put("value", result.suiteName());
        }
        return root.toString();
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    private void writeAttachmentPack(Path resultsDir, Path pack) throws IOException {
        Files.createDirectories(pack.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(pack, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
             Stream<Path> files = Files.list(resultsDir)) {
            for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                String fileName = file.getFileName().toString();
                if (!fileName.contains("-attachment")) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(fileName));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private void extractZip(InputStream in, Path target) throws IOException {
        int entries = 0;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("Слишком много файлов в архиве (лимит " + MAX_ZIP_ENTRIES + ")");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                long size = entry.getSize();
                if (size > MAX_ZIP_ENTRY_BYTES) {
                    throw new IllegalArgumentException("Файл " + entry.getName() + " больше лимита");
                }
                Path file = safeTarget(target, entry.getName());
                Files.createDirectories(file.getParent());
                long copied = Files.copy(zip, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                total += copied;
                if (total > MAX_ZIP_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Архив распаковывается больше лимита");
                }
            }
        }
    }

    private static Path safeTarget(Path targetDir, String entryName) {
        Path resolved = targetDir.resolve(entryName).normalize();
        if (!resolved.startsWith(targetDir)) {
            throw new IllegalArgumentException("Недопустимый путь в архиве: " + entryName);
        }
        return resolved;
    }

    /** Zip может содержать содержимое allure-results либо сам каталог в корне — находим каталог с *-result.json. */
    private static Path resolveResultsRoot(Path extracted) throws IOException {
        if (hasResultFiles(extracted)) {
            return extracted;
        }
        try (Stream<Path> dirs = Files.list(extracted)) {
            List<Path> sorted = dirs.sorted().toList();
            for (Path dir : sorted) {
                if (Files.isDirectory(dir) && hasResultFiles(dir)) {
                    return dir;
                }
            }
        }
        return extracted;
    }

    private static boolean hasResultFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith("-result.json"));
        }
    }

    private void applyRunSummary(TestRunEntity run, AllureParsedRun parsed, RunMeta meta) {
        if (run.getReportType() == null || run.getReportType() == ReportType.UNKNOWN) {
            run.setReportType(ReportType.TEST_RUN_SUMMARY);
        }
        if ((run.getTitle() == null || run.getTitle().isBlank())
                && (meta == null || meta.title() == null || meta.title().isBlank())) {
            run.setTitle("Результаты тестирования appScreener");
        } else if ((run.getTitle() == null || run.getTitle().isBlank()) && meta.title() != null) {
            run.setTitle(meta.title());
        }
        if (meta != null) {
            if (meta.standUrl() != null && !meta.standUrl().isBlank()) {
                run.setStandUrl(meta.standUrl().trim());
            }
            if (meta.ciSuites() != null && !meta.ciSuites().isBlank()) {
                run.setCiSuites(meta.ciSuites());
            }
            if (meta.pipelineUrl() != null && !meta.pipelineUrl().isBlank()) {
                run.setPipelineUrl(meta.pipelineUrl());
            }
        }
        if (!parsed.suites().isEmpty()) {
            run.setSuiteNamesJson(JsonUtil.toJson(parsed.suites()));
        }
        run.setTotalTests(parsed.results().size());
        run.setPassedTests(parsed.passedCount());
        run.setFailedTests(parsed.failedCount());
        run.setSkippedTests(parsed.skippedCount());
        run.setExecutionTime(formatDuration(parsed.maxStop() - parsed.minStart()));
        testRunRepository.save(run);
    }

    private TestRunEntity resolveRun(String runId, String threadId) {
        if (runId != null && !runId.isBlank()) {
            Optional<TestRunEntity> existing = testRunRepository.findById(runId.trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        CategoryInfo category = categoryService.resolveByThreadId(threadId)
                .filter(c -> !c.isAll())
                .orElse(CategoryInfo.fromEnum(ReportCategory.GENERAL));
        TestRunEntity run = new TestRunEntity();
        run.setId(newRunId());
        run.setCategoryCode(category.code());
        run.setReceivedAt(java.time.Instant.now());
        run.setReportType(ReportType.TEST_RUN_SUMMARY);
        run.setTitle("Результаты тестирования appScreener");
        return testRunRepository.save(run);
    }

    private void replacePreviousImport(String runId) {
        testResultRepository.deleteByRunIdIn(List.of(runId));
        attachmentRepository.deleteByRunIdIn(List.of(runId));
    }

    // ==== Чтение ====

    public Path attachmentPackPath(String runId) {
        return Path.of(storageDir).resolve(runId + "-attachments.zip");
    }

    /** Легаси-каталог loose-данных (до перехода на пак); используется только для чистки. */
    public Path runDirectory(String runId) {
        return Path.of(storageDir).resolve(runId);
    }

    public Optional<AllureTestResultEntity> findTestResult(String runId, String resultId) {
        return testResultRepository.findById(resultId)
                .filter(e -> e.getRunId().equals(runId));
    }

    public Optional<AllureAttachmentEntity> findAttachment(String source) {
        return attachmentRepository.findById(source);
    }

    /** Полный результат теста (шаги/аттачи/лейблы) — из сжатого JSON в БД, без диска. */
    public Optional<com.appscreener.report.model.allure.AllureRichResult> loadRichResult(String runId, String resultId) {
        return findTestResult(runId, resultId).flatMap(entity -> {
            byte[] gz = entity.getResultJsonGz();
            if (gz == null || gz.length == 0) {
                return Optional.empty();
            }
            try {
                return Optional.of(parser.parseFullResultGz(gz));
            } catch (IOException e) {
                log.warn("Не удалось разобрать result JSON теста {} прогона {}: {}", resultId, runId, e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * Открытый поток аттача из пака прогона; {@link #close()} закрывает и pak-файл.
     * Возвращается именно поток: аттач может быть большим (видео), в память не грузим.
     */
    public static final class AttachmentStream implements AutoCloseable {

        private final InputStream stream;
        private final ZipFile zipFile;
        private final long size;

        private AttachmentStream(InputStream stream, ZipFile zipFile, long size) {
            this.stream = stream;
            this.zipFile = zipFile;
            this.size = size;
        }

        public InputStream stream() {
            return stream;
        }

        public long size() {
            return size;
        }

        @Override
        public void close() throws IOException {
            try {
                stream.close();
            } finally {
                zipFile.close();
            }
        }
    }

    public Optional<AttachmentStream> openAttachmentStream(AllureAttachmentEntity attachment) throws IOException {
        Path pack = attachmentPackPath(attachment.getRunId());
        if (!Files.exists(pack)) {
            return Optional.empty();
        }
        ZipFile zip = new ZipFile(pack.toFile());
        ZipEntry entry = zip.getEntry(attachment.getSource());
        if (entry == null) {
            zip.close();
            return Optional.empty();
        }
        return Optional.of(new AttachmentStream(zip.getInputStream(entry), zip, entry.getSize()));
    }

    /** Текст аттача (до {@link #INLINE_TEXT_CAP_BYTES}) для inline-показа на странице теста. */
    private String readInlineText(AllureAttachmentEntity attachment) {
        try (AttachmentStream handle = openAttachmentStream(attachment).orElse(null)) {
            if (handle == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            long read = 0;
            int n;
            while (read < INLINE_TEXT_CAP_BYTES && (n = handle.stream().read(chunk)) != -1) {
                int take = (int) Math.min(n, INLINE_TEXT_CAP_BYTES - read);
                buffer.write(chunk, 0, take);
                read += take;
            }
            String text = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (read >= INLINE_TEXT_CAP_BYTES && handle.stream().read() != -1) {
                text += "\n… (обрезано, полный файл — по ссылке)";
            }
            return text;
        } catch (IOException e) {
            return null;
        }
    }

    /** Модель страницы теста: сущность + шаги + аттачи (с inline-текстом для text/*). */
    public Optional<AllureTestPageView> buildTestPage(String runId, String resultId) {
        Optional<AllureTestResultEntity> entityOpt = findTestResult(runId, resultId);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }
        AllureTestResultEntity entity = entityOpt.get();
        AllureTestPageView view = new AllureTestPageView();
        view.setRunId(runId);
        testRunRepository.findById(runId)
                .map(TestRunEntity::getTitle)
                .ifPresent(view::setRunTitle);
        view.setResultId(resultId);
        view.setName(entity.getName() != null ? entity.getName() : entity.getFullName());
        view.setFullName(entity.getFullName());
        view.setStatus(entity.getStatus());
        view.setStatusMessage(entity.getStatusMessage());
        view.setStartMs(entity.getStartMs());
        view.setStopMs(entity.getStopMs());
        view.setDurationMs(duration(entity));
        view.setSuiteName(entity.getSuiteName());
        view.setFlaky(entity.isFlaky());

        loadRichResult(runId, resultId).ifPresent(rich -> {
            view.setStatusTrace(rich.statusTrace());
            view.setSteps(rich.steps());
            view.setStepRows(flattenSteps(rich.steps()));
            view.setParameters(rich.parameters());
            view.setStatusMessage(rich.statusMessage() != null ? rich.statusMessage() : entity.getStatusMessage());

            List<AllureAttachmentRef> refs = new ArrayList<>(rich.attachments());
            collectStepAttachments(rich.steps(), refs);
            List<AllureTestPageView.AttachmentView> attachments = new ArrayList<>();
            java.util.Set<String> seenSources = new java.util.HashSet<>();
            for (AllureAttachmentRef ref : refs) {
                if (!seenSources.add(ref.source())) {
                    continue;
                }
                attachmentRepository.findById(ref.source()).ifPresent(att ->
                        attachments.add(toAttachmentView(ref, att)));
            }
            view.setAttachments(attachments);
        });
        return Optional.of(view);
    }

    private static long duration(AllureTestResultEntity entity) {
        Long start = entity.getStartMs();
        Long stop = entity.getStopMs();
        if (start == null || stop == null || stop < start) {
            return 0;
        }
        return stop - start;
    }

    private static void collectStepAttachments(List<com.appscreener.report.model.allure.AllureStepNode> steps,
                                               List<AllureAttachmentRef> refs) {
        for (com.appscreener.report.model.allure.AllureStepNode step : steps) {
            refs.addAll(step.attachments());
            collectStepAttachments(step.steps(), refs);
        }
    }

    /** Дерево шагов → плоский список с глубиной (для шаблона без рекурсивных фрагментов). */
    private static List<com.appscreener.report.model.allure.StepRow> flattenSteps(
            List<com.appscreener.report.model.allure.AllureStepNode> steps) {
        List<com.appscreener.report.model.allure.StepRow> rows = new ArrayList<>();
        flattenInto(steps, 0, rows);
        return rows;
    }

    private static void flattenInto(List<com.appscreener.report.model.allure.AllureStepNode> steps, int depth,
                                    List<com.appscreener.report.model.allure.StepRow> rows) {
        for (com.appscreener.report.model.allure.AllureStepNode step : steps) {
            rows.add(new com.appscreener.report.model.allure.StepRow(step, depth));
            flattenInto(step.steps(), depth + 1, rows);
        }
    }

    private AllureTestPageView.AttachmentView toAttachmentView(AllureAttachmentRef ref, AllureAttachmentEntity att) {
        String contentType = ref.type() != null && !ref.type().isBlank() ? ref.type() : att.getContentType();
        String inlineText = null;
        if (contentType != null && (contentType.startsWith("text/") || contentType.contains("json")
                || contentType.contains("xml") || contentType.contains("markdown"))) {
            inlineText = readInlineText(att);
        }
        String displayName = ref.name() != null && !ref.name().isBlank() ? ref.name() : att.getSource();
        return new AllureTestPageView.AttachmentView(att.getSource(), displayName, contentType,
                att.getSizeBytes(), inlineText);
    }

    /** Чистка allure-данных прогонов; вызывается retention-ом. */
    @Transactional
    public void deleteRunData(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        testResultRepository.deleteByRunIdIn(runIds);
        attachmentRepository.deleteByRunIdIn(runIds);
        for (String runId : runIds) {
            try {
                Files.deleteIfExists(attachmentPackPath(runId));
            } catch (IOException e) {
                log.warn("Не удалось удалить пак аттачей {}: {}", runId, e.getMessage());
            }
            deleteRecursive(runDirectory(runId));
        }
    }

    private static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    static String formatDuration(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        return "text/plain";
    }

    private static void deleteRecursive(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Не удалось удалить {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Не удалось удалить каталог {}: {}", path, e.getMessage());
        }
    }
}
