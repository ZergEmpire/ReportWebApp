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

    function revokeObjectUrl(target) {
        if (target?.dataset?.objectUrl) {
            URL.revokeObjectURL(target.dataset.objectUrl);
            delete target.dataset.objectUrl;
        }
    }

    function buildAttachmentHtmlDocument(html) {
        return '<!DOCTYPE html><html lang="ru"><head><meta charset="UTF-8">' +
            '<meta name="viewport" content="width=device-width, initial-scale=1">' +
            '<style>' +
            'html,body{margin:0;padding:0;background:#0f0f14;color:#e8e4d9;font:14px/1.5 Segoe UI,system-ui,sans-serif;}' +
            'body{padding:16px;}' +
            '*{box-sizing:border-box;max-width:100%;}' +
            'a{color:#e8c547;}' +
            'table{width:max-content;min-width:100%;border-collapse:collapse;display:block;overflow:auto;background:#111118;}' +
            'th,td{border:1px solid rgba(255,255,255,.12);padding:8px 10px;vertical-align:top;text-align:left;white-space:pre-wrap;}' +
            'th{background:#1a1a24;color:#e8c547;position:sticky;top:0;}' +
            'pre,code{font:12px/1.45 Consolas,monospace;white-space:pre-wrap;word-break:break-word;}' +
            'pre{background:#08080a;border:1px solid rgba(255,255,255,.08);border-radius:8px;padding:12px;overflow:auto;}' +
            'img{height:auto;}' +
            '.table-wrap{overflow:auto;max-width:100%;}' +
            '</style></head><body><div class="table-wrap">' + html + '</div></body></html>';
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
            revokeObjectUrl(frame);
            text.hidden = true;
            text.textContent = '';
            revokeObjectUrl(text);
            revokeObjectUrl(panel);
            actions.hidden = true;
            openLink.href = '#';
            btn.setAttribute('aria-expanded', 'true');

            try {
                var res = await fetch('/api/allure/testresult/' + id + '/attachment');
                var contentType = (res.headers.get('content-type') || '').toLowerCase();

                if (!res.ok) {
                    var errorText = await readErrorText(res, contentType);
                    if (res.status !== 404) {
                        panel.classList.add('is-error');
                    }
                    placeholder.textContent = errorText;
                    return;
                }

                if (contentType.includes('text/html')) {
                    var html = await res.text();
                    var renderedHtml = buildAttachmentHtmlDocument(html);
                    var htmlUrl = URL.createObjectURL(new Blob([renderedHtml], { type: contentType || 'text/html' }));
                    frame.dataset.objectUrl = htmlUrl;
                    frame.srcdoc = renderedHtml;
                    frame.hidden = false;
                    placeholder.hidden = true;
                    openLink.href = htmlUrl;
                    actions.hidden = false;
                } else if (contentType.startsWith('text/') || contentType.includes('json') || contentType.includes('xml')) {
                    var textValue = await res.text();
                    var textUrl = URL.createObjectURL(new Blob([textValue], { type: contentType || 'text/plain' }));
                    text.dataset.objectUrl = textUrl;
                    text.textContent = textValue;
                    text.hidden = false;
                    placeholder.hidden = true;
                    openLink.href = textUrl;
                    actions.hidden = false;
                } else {
                    var blob = await res.blob();
                    var blobUrl = URL.createObjectURL(blob);
                    panel.dataset.objectUrl = blobUrl;
                    openLink.href = blobUrl;
                    actions.hidden = false;
                    placeholder.textContent = 'Attachment готов. Откройте его отдельно.';
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
