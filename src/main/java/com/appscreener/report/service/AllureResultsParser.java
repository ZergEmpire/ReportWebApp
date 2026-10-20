package com.appscreener.report.service;

import com.appscreener.report.model.allure.AllureAttachmentRef;
import com.appscreener.report.model.allure.AllureParam;
import com.appscreener.report.model.allure.AllureParsedResult;
import com.appscreener.report.model.allure.AllureParsedRun;
import com.appscreener.report.model.allure.AllureRichResult;
import com.appscreener.report.model.allure.AllureStepNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Чтение каталога allure-results в том же формате, который генерируют allure-адаптеры
 * (allure-testng / allure-java): {@code *-result.json}, {@code *-container.json}, {@code *-attachment.*}.
 */
@Service
public class AllureResultsParser {

    /** statusDetails.message длиннее — обрезаем при импорте (полный текст остаётся в raw-файле). */
    static final int MESSAGE_CAP = 8000;

    private static final Set<String> RESULT_STATUSES = Set.of("passed", "failed", "broken", "skipped", "unknown");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Разбор каталога: сводка прогона + упрощённые результаты (без дерева шагов). */
    public AllureParsedRun parseDirectory(Path resultsDir) throws IOException {
        List<Path> resultFiles = new ArrayList<>();
        Map<String, ContainerNode> containers = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(resultsDir)) {
            files.sorted().forEach(p -> {
                String fileName = p.getFileName().toString();
                if (fileName.endsWith("-result.json")) {
                    resultFiles.add(p);
                } else if (fileName.endsWith("-container.json")) {
                    parseContainer(p, containers);
                }
            });
        }

        Map<String, String> childToSuite = buildChildToSuite(containers);
        List<AllureParsedResult> results = new ArrayList<>();
        for (Path file : resultFiles) {
            AllureParsedResult parsed = parseResultSummary(file, childToSuite);
            if (parsed != null) {
                results.add(parsed);
            }
        }
        results.sort(Comparator
                .comparing(AllureParsedResult::start, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AllureParsedResult::uuid));

        List<String> suites = suiteNamesInContainerOrder(results);
        long minStart = results.stream().map(AllureParsedResult::start).filter(s -> s != null && s > 0)
                .min(Long::compareTo).orElse(0L);
        long maxStop = results.stream().map(AllureParsedResult::stop).filter(s -> s != null && s > 0)
                .max(Long::compareTo).orElse(0L);
        return new AllureParsedRun(results, suites, minStart, maxStop);
    }

    /** Полный разбор одного {@code *-result.json} (для страницы теста). */
    public AllureRichResult parseFullResult(Path resultFile) throws IOException {
        JsonNode root = objectMapper.readTree(resultFile.toFile());
        JsonNode statusDetails = root.path("statusDetails");
        return new AllureRichResult(
                text(root, "uuid"),
                text(root, "name"),
                text(root, "fullName"),
                normalizeStatus(text(root, "status")),
                statusDetails.path("known").asBoolean(false),
                statusDetails.path("flaky").asBoolean(false),
                statusDetails.path("muted").asBoolean(false),
                text(statusDetails, "message"),
                text(statusDetails, "trace"),
                root.hasNonNull("start") ? root.get("start").asLong() : null,
                root.hasNonNull("stop") ? root.get("stop").asLong() : null,
                readLabels(root),
                readSteps(root.path("steps")),
                readAttachmentRefs(root.path("attachments")),
                readParams(root.path("parameters")));
    }

    /** Список файлов-аттачей каталога: source (имя файла) → путь. */
    public Map<String, Path> attachmentFiles(Path resultsDir) throws IOException {
        Map<String, Path> attachments = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(resultsDir)) {
            files.forEach(p -> {
                String fileName = p.getFileName().toString();
                if (fileName.contains("-attachment")) {
                    attachments.put(fileName, p);
                }
            });
        }
        return attachments;
    }

    /**
     * Метаданные аттачей из JSON-рефов ({@code {name, source, type}}) всех result/container файлов:
     * source → ref. Тип из JSON авторитетнее расширения файла (бывают аттачи без расширения
     * с {@code "type":"image/png"}).
     */
    public Map<String, AllureAttachmentRef> attachmentRefs(Path resultsDir) throws IOException {
        Map<String, AllureAttachmentRef> refs = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(resultsDir)) {
            for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith("-result.json") && !fileName.endsWith("-container.json")) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(file.toFile());
                    collectRefs(root, refs);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Не удалось прочитать " + fileName + " для рефов аттачей: " + e.getMessage(), e);
                }
            }
        }
        return refs;
    }

    private void collectRefs(JsonNode node, Map<String, AllureAttachmentRef> refs) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectRefs(item, refs);
            }
            return;
        }
        JsonNode attachments = node.get("attachments");
        if (attachments != null && attachments.isArray()) {
            for (JsonNode attachment : attachments) {
                String source = text(attachment, "source");
                if (source == null || refs.containsKey(source)) {
                    continue;
                }
                refs.put(source, new AllureAttachmentRef(
                        text(attachment, "name"), source, text(attachment, "type")));
            }
        }
        for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value.isObject() || value.isArray()) {
                collectRefs(value, refs);
            }
        }
    }

    private void parseContainer(Path file, Map<String, ContainerNode> containers) {
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            String uuid = text(root, "uuid");
            if (uuid == null || uuid.isBlank()) {
                return;
            }
            List<String> children = new ArrayList<>();
            JsonNode childrenNode = root.path("children");
            if (childrenNode.isArray()) {
                for (JsonNode child : childrenNode) {
                    String childUuid = child.asText(null);
                    if (childUuid != null && !childUuid.isBlank()) {
                        children.add(childUuid);
                    }
                }
            }
            containers.put(uuid, new ContainerNode(uuid, text(root, "name"), children,
                    root.hasNonNull("start") ? root.get("start").asLong() : 0L));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать container " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /** uuid результата → имя корневого контейнера-сьюиты (контейнеры могут быть вложены через children). */
    private Map<String, String> buildChildToSuite(Map<String, ContainerNode> containers) {
        Map<String, String> parent = new HashMap<>();
        for (ContainerNode container : containers.values()) {
            for (String child : container.children()) {
                if (containers.containsKey(child)) {
                    parent.put(child, container.uuid());
                }
            }
        }
        Map<String, String> childToSuite = new HashMap<>();
        for (ContainerNode container : containers.values()) {
            String rootName = rootContainer(container.uuid(), containers, parent).name();
            for (String child : container.children()) {
                if (!containers.containsKey(child) && !childToSuite.containsKey(child)
                        && rootName != null && !rootName.isBlank()) {
                    childToSuite.put(child, rootName);
                }
            }
        }
        return childToSuite;
    }

    private ContainerNode rootContainer(String uuid, Map<String, ContainerNode> containers, Map<String, String> parent) {
        ContainerNode current = containers.get(uuid);
        Set<String> visited = new LinkedHashSet<>();
        while (current != null) {
            String parentUuid = parent.get(current.uuid());
            if (parentUuid == null || !visited.add(parentUuid)) {
                return current;
            }
            ContainerNode parentNode = containers.get(parentUuid);
            if (parentNode == null) {
                return current;
            }
            current = parentNode;
        }
        return new ContainerNode(uuid, null, List.of(), 0L);
    }

    /** Имена сьют для сводки: человекочитаемые suiteName результатов по порядку запуска
     *  (сырые имена fixture-контейнеров вида package.Class.method не попадают). */
    private List<String> suiteNamesInContainerOrder(List<AllureParsedResult> results) {
        Set<String> suites = new LinkedHashSet<>();
        results.forEach(r -> {
            if (r.suiteName() != null && !r.suiteName().isBlank()) {
                suites.add(r.suiteName());
            }
        });
        return new ArrayList<>(suites);
    }

    private AllureParsedResult parseResultSummary(Path file, Map<String, String> childToSuite) {
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            String uuid = text(root, "uuid");
            if (uuid == null || uuid.isBlank()) {
                uuid = stripSuffix(file.getFileName().toString(), "-result.json");
            }
            JsonNode statusDetails = root.path("statusDetails");
            String message = text(statusDetails, "message");
            if (message != null && message.length() > MESSAGE_CAP) {
                message = message.substring(0, MESSAGE_CAP) + "…";
            }
            return new AllureParsedResult(
                    uuid,
                    text(root, "name"),
                    text(root, "fullName"),
                    normalizeStatus(text(root, "status")),
                    statusDetails.path("flaky").asBoolean(false),
                    message,
                    root.hasNonNull("start") ? root.get("start").asLong() : null,
                    root.hasNonNull("stop") ? root.get("stop").asLong() : null,
                    resolveSuiteName(root, uuid, childToSuite));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать result " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private String resolveSuiteName(JsonNode result, String uuid, Map<String, String> childToSuite) {
        // человекочитаемое имя сюиты: label suite → корневой контейнер → label parentSuite
        String suiteLabel = firstLabel(result, "suite");
        if (suiteLabel != null) {
            return suiteLabel;
        }
        String fromContainer = childToSuite.get(uuid);
        if (fromContainer != null && !fromContainer.isBlank()) {
            return fromContainer;
        }
        return firstLabel(result, "parentSuite");
    }

    private List<AllureStepNode> readSteps(JsonNode stepsNode) {
        List<AllureStepNode> steps = new ArrayList<>();
        if (stepsNode != null && stepsNode.isArray()) {
            for (JsonNode node : stepsNode) {
                JsonNode statusDetails = node.path("statusDetails");
                steps.add(new AllureStepNode(
                        text(node, "name"),
                        normalizeStatus(text(node, "status")),
                        text(node, "stage"),
                        node.hasNonNull("start") ? node.get("start").asLong() : null,
                        node.hasNonNull("stop") ? node.get("stop").asLong() : null,
                        text(statusDetails, "message"),
                        text(statusDetails, "trace"),
                        readSteps(node.path("steps")),
                        readAttachmentRefs(node.path("attachments")),
                        readParams(node.path("parameters"))));
            }
        }
        return steps;
    }

    private List<AllureAttachmentRef> readAttachmentRefs(JsonNode node) {
        List<AllureAttachmentRef> refs = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode attachment : node) {
                String source = text(attachment, "source");
                if (source == null || source.isBlank()) {
                    continue;
                }
                refs.add(new AllureAttachmentRef(text(attachment, "name"), source, text(attachment, "type")));
            }
        }
        return refs;
    }

    private List<AllureParam> readParams(JsonNode node) {
        List<AllureParam> params = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode param : node) {
                params.add(new AllureParam(text(param, "name"), text(param, "value")));
            }
        }
        return params;
    }

    private Map<String, List<String>> readLabels(JsonNode root) {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        JsonNode node = root.path("labels");
        if (node.isArray()) {
            for (JsonNode label : node) {
                String name = text(label, "name");
                String value = text(label, "value");
                if (name == null || value == null) {
                    continue;
                }
                labels.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            }
        }
        return labels;
    }

    private String firstLabel(JsonNode result, String labelName) {
        JsonNode labels = result.path("labels");
        if (labels.isArray()) {
            for (JsonNode label : labels) {
                if (labelName.equals(text(label, "name"))) {
                    String value = text(label, "value");
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank() || !RESULT_STATUSES.contains(status)) {
            return "unknown";
        }
        return status;
    }

    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = value.asText();
        return s.isBlank() ? null : s;
    }

    private record ContainerNode(String uuid, String name, List<String> children, long start) {
    }
}
