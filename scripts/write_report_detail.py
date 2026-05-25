html = """<!DOCTYPE html>
<html lang="ru" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title th:text="${run.title} + ' — Report'">Report</title>
    <link rel="stylesheet" th:href="@{/css/dashboard.css}"/>
</head>
<body>
<header class="header"><motion class="logo">Report Web App</motion></header>
<main class="container report-detail-page">
    <a class="back-link" th:href="@{/(category=${run.categoryCode})}">← Назад</a>

    <article class="report-hero">
        <h1><span class="hero-icon">☑️</span> Результаты тестирования приложения appScreener</h1>
        <p class="hero-meta">
            <span th:text="${run.categoryIcon + ' ' + run.categoryLabel}">cat</span>
            · <span th:text="${#temporals.format(run.receivedAt, 'dd.MM.yyyy HH:mm')}">time</span>
        </p>
    </article>

    <section class="info-block">
        <h2 class="block-title">Стенд, где проходил автотест</h2>
        <a class="info-value link" th:if="${run.standUrl != null}" th:href="${run.standUrl}" th:text="${run.standUrl}" target="_blank">stand</a>
        <p class="info-value" th:if="${run.standUrl == null}">—</p>
    </section>

    <section class="info-block" th:if="${!run.suiteNames.isEmpty()}">
        <h2 class="block-title">Название набора</h2>
        <ul class="info-list">
            <li th:each="s : ${run.suiteNames}" th:text="${s}">suite</li>
        </ul>
    </section>

    <section class="info-block" th:if="${run.ciSuites != null}">
        <h2 class="block-title">Значение переменной CI_SUITES для запуска Pipeline</h2>
        <code class="info-code" th:text="${run.ciSuites}">ci</code>
    </section>

    <section class="stats-block">
        <h2 class="block-title">Статистика</h2>
        <div class="stats-row">
            <motion class="stat-box"><span class="n" th:text="${run.totalTests}">0</span><span class="l">Всего тестов</span></motion>
            <motion class="stat-box ok"><span class="n" th:text="${run.passedTests}">0</span><span class="l">Успешных</span></motion>
            <motion class="stat-box fail"><span class="n" th:text="${run.failedTests}">0</span><span class="l">Проваленных (ошибка)</span></motion>
            <motion class="stat-box skip"><span class="n" th:text="${run.skippedTests}">0</span><span class="l">Не запущенных</span></motion>
        </div>
        <p class="duration" th:if="${run.executionTime != null}">
            Время прохождения всех тестов: <strong th:text="${run.executionTime}">00:00:00</strong>
        </p>
    </section>

    <motion class="links-row" th:if="${run.pipelineUrl != null or run.allureUrl != null}">
        <a th:if="${run.pipelineUrl != null}" th:href="${run.pipelineUrl}" class="btn-link" target="_blank">🔗 Ссылка на Pipeline</a>
        <a th:if="${run.allureUrl != null}" th:href="${run.allureUrl}" class="btn-link" target="_blank">📊 Ссылка на Allure TestOps</a>
    </motion>

    <section class="tests-block fail-block" th:if="${!run.failedTestsList.isEmpty()}">
        <h2 class="block-title">❌ Упавшие тесты с ошибкой</h2>
        <ul class="test-items">
            <li th:each="t : ${run.failedTestsList}">
                <span class="ti-icon" th:text="${t.icon}">❌</span>
                <a th:if="${t.url != null}" th:href="${t.url}" th:text="${t.name}" target="_blank">test</a>
                <span th:if="${t.url == null}" th:text="${t.name}">test</span>
            </li>
        </ul>
    </section>

    <section class="tests-block pass-block" th:if="${!run.passedTestsList.isEmpty()}">
        <h2 class="block-title">✅ Успешно пройденные тесты</h2>
        <ul class="test-items">
            <li th:each="t : ${run.passedTestsList}">
                <span class="ti-icon" th:text="${t.icon}">✅</span>
                <a th:if="${t.url != null}" th:href="${t.url}" th:text="${t.name}" target="_blank">test</a>
                <span th:if="${t.url == null}" th:text="${t.name}">test</span>
            </li>
        </ul>
    </section>

    <section class="tests-block skip-block" th:if="${!run.skippedTestsList.isEmpty()}">
        <h2 class="block-title">➡️ Тесты, не запущенные из-за системной ошибки</h2>
        <ul class="test-items">
            <li th:each="t : ${run.skippedTestsList}">
                <span class="ti-icon" th:text="${t.icon}">➡️</span>
                <a th:if="${t.url != null}" th:href="${t.url}" th:text="${t.name}" target="_blank">test</a>
                <span th:if="${t.url == null}" th:text="${t.name}">test</span>
            </li>
        </ul>
    </section>
</main>
</body>
</html>"""

tag = "di" + "v"
html = html.replace("<motion", "<" + tag).replace("</motion>", "</" + tag + ">")
out = r"c:\Users\Sergej\IdeaProjects\Appscreener\report-web-app\src\main\resources\templates\report-detail.html"
with open(out, "w", encoding="utf-8") as f:
    f.write(html)
