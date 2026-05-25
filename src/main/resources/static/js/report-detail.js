/**
 * Загрузка stacktrace из Allure TestOps для упавших тестов на странице прогона.
 */
(function () {
    document.querySelectorAll('.js-stacktrace-toggle').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var id = btn.getAttribute('data-allure-id');
            var panel = btn.closest('.test-item-body')?.querySelector('.js-stacktrace-panel');
            if (!id || !panel) {
                return;
            }

            if (panel.dataset.loaded === 'true' && !panel.hidden) {
                panel.hidden = true;
                btn.setAttribute('aria-expanded', 'false');
                btn.textContent = btn.dataset.labelDefault || 'Stacktrace из Allure';
                return;
            }

            var labelDefault = btn.dataset.labelDefault || btn.textContent;
            btn.dataset.labelDefault = labelDefault;
            btn.disabled = true;
            btn.classList.add('is-busy');
            btn.textContent = 'Загрузка…';
            panel.hidden = false;
            panel.classList.remove('is-error');
            panel.classList.add('is-loading');
            panel.textContent = 'Запрос к Allure TestOps…';
            btn.setAttribute('aria-expanded', 'true');

            try {
                var res = await fetch('/api/allure/testresult/' + id);
                var data = await res.json();
                panel.classList.remove('is-loading');

                if (!res.ok) {
                    panel.classList.add('is-error');
                    panel.textContent = data.error || ('HTTP ' + res.status);
                    return;
                }

                var parts = [];
                if (data.status) {
                    parts.push('Статус: ' + data.status);
                }
                if (data.message) {
                    parts.push('Message:\n' + data.message);
                }
                if (data.trace) {
                    parts.push('Stacktrace:\n' + data.trace);
                }
                if (!data.message && !data.trace) {
                    parts.push('Нет message/trace в ответе API');
                }
                if (data.allureUiUrl) {
                    parts.push('Открыть в TestOps: ' + data.allureUiUrl);
                }

                panel.textContent = parts.join('\n\n');
                panel.dataset.loaded = 'true';
                btn.textContent = 'Скрыть stacktrace';
            } catch (e) {
                panel.classList.remove('is-loading');
                panel.classList.add('is-error');
                panel.textContent = 'Ошибка: ' + e.message;
            } finally {
                btn.disabled = false;
                btn.classList.remove('is-busy');
                if (btn.textContent === 'Загрузка…') {
                    btn.textContent = labelDefault;
                }
            }
        });
    });
})();
