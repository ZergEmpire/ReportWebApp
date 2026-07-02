/**
 * Stacktrace, скриншот и attachment из Allure TestOps на странице прогона.
 */
(function () {
    function getTestItemBody(btn) {
        return btn.closest('.test-item-body');
    }

    function setBusy(btn, busy, busyText) {
        var labelDefault = btn.dataset.labelDefault || btn.textContent;
        if (!btn.dataset.labelDefault) {
            btn.dataset.labelDefault = labelDefault;
        }
        btn.disabled = busy;
        btn.classList.toggle('is-busy', busy);
        if (busy) {
            btn.textContent = busyText || 'Загрузка…';
        }
    }

    function readErrorText(res, contentType) {
        if (contentType.includes('application/json')) {
            return res.json().then(function (data) {
                return data.error || ('HTTP ' + res.status);
            });
        }
        return res.text().then(function (text) {
            return text || ('HTTP ' + res.status);
        });
    }

    document.querySelectorAll('.js-stacktrace-toggle').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var id = btn.getAttribute('data-allure-id');
            var panel = getTestItemBody(btn)?.querySelector('.js-stacktrace-panel');
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
            setBusy(btn, true);
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
                setBusy(btn, false);
                if (btn.textContent === 'Загрузка…') {
                    btn.textContent = labelDefault;
                }
            }
        });
    });

    document.querySelectorAll('.js-screenshot-toggle').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var id = btn.getAttribute('data-allure-id');
            var body = getTestItemBody(btn);
            var panel = body?.querySelector('.js-screenshot-panel');
            var img = panel?.querySelector('.screenshot-img');
            var placeholder = panel?.querySelector('.screenshot-placeholder');
            if (!id || !panel || !img || !placeholder) {
                return;
            }

            if (panel.dataset.loaded === 'true' && !panel.hidden) {
                panel.hidden = true;
                btn.setAttribute('aria-expanded', 'false');
                btn.textContent = btn.dataset.labelDefault || 'Скриншот падения';
                return;
            }

            var labelDefault = btn.dataset.labelDefault || btn.textContent;
            setBusy(btn, true, 'Загрузка…');
            panel.hidden = false;
            panel.classList.remove('is-error');
            img.hidden = true;
            img.removeAttribute('src');
            placeholder.hidden = false;
            placeholder.textContent = 'Запрос к Allure TestOps…';
            btn.setAttribute('aria-expanded', 'true');

            try {
                var res = await fetch('/api/allure/testresult/' + id + '/screenshot');
                var contentType = res.headers.get('content-type') || '';

                if (!res.ok) {
                    panel.classList.add('is-error');
                    var errText = await readErrorText(res, contentType);
                    placeholder.hidden = false;
                    placeholder.textContent = errText;
                    return;
                }

                if (!contentType.startsWith('image/')) {
                    panel.classList.add('is-error');
                    placeholder.hidden = false;
                    placeholder.textContent = 'Ответ Allure не является изображением';
                    return;
                }

                var blob = await res.blob();
                if (panel.dataset.objectUrl) {
                    URL.revokeObjectURL(panel.dataset.objectUrl);
                }
                var objectUrl = URL.createObjectURL(blob);
                panel.dataset.objectUrl = objectUrl;
                img.src = objectUrl;
                img.hidden = false;
                placeholder.hidden = true;
                panel.classList.remove('is-error');
                panel.dataset.loaded = 'true';
                btn.textContent = 'Скрыть скриншот';
            } catch (e) {
                panel.classList.add('is-error');
                placeholder.hidden = false;
                placeholder.textContent = 'Ошибка: ' + e.message;
            } finally {
                setBusy(btn, false);
                if (btn.textContent === 'Загрузка…') {
                    btn.textContent = labelDefault;
                }
            }
        });
    });

    document.querySelectorAll('.js-attachment-toggle').forEach(function (btn) {
        btn.addEventListener('click', async function () {
            var id = btn.getAttribute('data-allure-id');
            var body = getTestItemBody(btn);
            var panel = body?.querySelector('.js-attachment-panel');
            var frame = panel?.querySelector('.js-attachment-frame');
            var text = panel?.querySelector('.js-attachment-text');
            var placeholder = panel?.querySelector('.attachment-placeholder');
            var actions = panel?.querySelector('.attachment-actions');
            var openLink = panel?.querySelector('.js-attachment-open');
            if (!id || !panel || !frame || !text || !placeholder || !actions || !openLink) {
                return;
            }

            if (panel.dataset.loaded === 'true' && !panel.hidden) {
                panel.hidden = true;
                btn.setAttribute('aria-expanded', 'false');
                btn.textContent = btn.dataset.labelDefault || 'Attachment из Allure';
                return;
            }

            var labelDefault = btn.dataset.labelDefault || btn.textContent;
            setBusy(btn, true, 'Загрузка…');
            panel.hidden = false;
            panel.classList.remove('is-error');
            placeholder.hidden = false;
            placeholder.textContent = 'Запрос к Allure TestOps…';
            frame.hidden = true;
            frame.removeAttribute('srcdoc');
            text.hidden = true;
            text.textContent = '';
            actions.hidden = true;
            openLink.href = '#';
            btn.setAttribute('aria-expanded', 'true');

            try {
                var res = await fetch('/api/allure/testresult/' + id + '/attachment');
                var contentType = (res.headers.get('content-type') || '').toLowerCase();

                if (!res.ok) {
                    panel.classList.add('is-error');
                    placeholder.textContent = await readErrorText(res, contentType);
                    return;
                }

                var attachmentUrl = '/api/allure/testresult/' + id + '/attachment';
                openLink.href = attachmentUrl;
                actions.hidden = false;

                if (contentType.includes('text/html')) {
                    var html = await res.text();
                    frame.srcdoc = html;
                    frame.hidden = false;
                    placeholder.hidden = true;
                } else if (contentType.startsWith('text/') || contentType.includes('json') || contentType.includes('xml')) {
                    text.textContent = await res.text();
                    text.hidden = false;
                    placeholder.hidden = true;
                } else {
                    placeholder.textContent = 'Attachment готов. Лучше открыть его отдельно.';
                }

                panel.classList.remove('is-error');
                panel.dataset.loaded = 'true';
                btn.textContent = 'Скрыть attachment';
            } catch (e) {
                panel.classList.add('is-error');
                placeholder.hidden = false;
                placeholder.textContent = 'Ошибка: ' + e.message;
            } finally {
                setBusy(btn, false);
                if (btn.textContent === 'Загрузка…') {
                    btn.textContent = labelDefault;
                }
            }
        });
    });
})();
