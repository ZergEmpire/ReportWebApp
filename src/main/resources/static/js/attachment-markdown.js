/**
 * Общий рендер markdown-аттачей (заголовки/таблицы/ссылки) в HTML.
 * На странице allure-теста применяется автоматически к pre.js-markdown-text;
 * из report-detail.js используется через window.AttachmentMarkdown.
 */
(function () {
    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function isTableSeparator(line) {
        return /^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$/.test(line);
    }

    function splitTableRow(line) {
        var trimmed = line.trim();
        if (trimmed.startsWith('|')) {
            trimmed = trimmed.slice(1);
        }
        if (trimmed.endsWith('|')) {
            trimmed = trimmed.slice(0, -1);
        }
        return trimmed.split('|').map(function (cell) {
            return cell.trim();
        });
    }

    function inlineMarkdown(text) {
        var escaped = escapeHtml(text);
        escaped = escaped.replace(/\[([^\]]+)\]\(([^)]+)\)/g, function (_match, label, url) {
            return '<a href="' + escapeHtml(url) + '" target="_blank" rel="noopener">' + escapeHtml(label) + '</a>';
        });
        escaped = escaped.replace(/(https?:\/\/[^\s<]+)/g, function (_match, url) {
            return '<a href="' + escapeHtml(url) + '" target="_blank" rel="noopener">' + escapeHtml(url) + '</a>';
        });
        return escaped;
    }

    function renderMarkdownTable(headers, rows) {
        var html = ['<table><thead><tr>'];
        headers.forEach(function (header) {
            html.push('<th>', inlineMarkdown(header), '</th>');
        });
        html.push('</tr></thead><tbody>');
        rows.forEach(function (row) {
            html.push('<tr>');
            for (var i = 0; i < headers.length; i++) {
                html.push('<td>', inlineMarkdown(row[i] || ''), '</td>');
            }
            html.push('</tr>');
        });
        html.push('</tbody></table>');
        return html.join('');
    }

    function markdownToHtml(text) {
        var lines = String(text).replace(/\r\n/g, '\n').split('\n');
        var html = [];
        var i = 0;
        while (i < lines.length) {
            var trimmed = lines[i].trim();
            if (!trimmed) {
                i++;
                continue;
            }
            var headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/);
            if (headingMatch) {
                var level = headingMatch[1].length;
                html.push('<h' + level + '>' + inlineMarkdown(headingMatch[2]) + '</h' + level + '>');
                i++;
                continue;
            }
            if (trimmed.includes('|') && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
                var headerCells = splitTableRow(lines[i]);
                i += 2;
                var rows = [];
                while (i < lines.length) {
                    var rowLine = lines[i].trim();
                    if (!rowLine || !rowLine.includes('|') || isTableSeparator(rowLine)) {
                        break;
                    }
                    rows.push(splitTableRow(lines[i]));
                    i++;
                }
                html.push(renderMarkdownTable(headerCells, rows));
                continue;
            }
            var paragraph = [];
            while (i < lines.length) {
                var paragraphLine = lines[i].trim();
                if (!paragraphLine) {
                    break;
                }
                if (/^(#{1,6})\s+\S/.test(paragraphLine)) {
                    break;
                }
                if (paragraphLine.includes('|') && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
                    break;
                }
                paragraph.push(paragraphLine);
                i++;
            }
            if (paragraph.length) {
                html.push('<p>' + inlineMarkdown(paragraph.join(' ')) + '</p>');
            }
        }
        return html.join('\n');
    }

    function looksLikeMarkdown(text, contentType, attachmentName) {
        if ((contentType || '').includes('markdown')) {
            return true;
        }
        if ((attachmentName || '').toLowerCase().endsWith('.md')) {
            return true;
        }
        if (!text) {
            return false;
        }
        var lines = String(text).split(/\r?\n/);
        var hasHeading = lines.some(function (line) {
            return /^#{1,6}\s+\S/.test(line.trim());
        });
        var hasPipeTable = false;
        for (var i = 0; i < lines.length - 1; i++) {
            if (lines[i].includes('|') && isTableSeparator(lines[i + 1])) {
                hasPipeTable = true;
                break;
            }
        }
        return hasPipeTable || (hasHeading && text.indexOf('|') !== -1);
    }

    window.AttachmentMarkdown = {
        markdownToHtml: markdownToHtml,
        looksLikeMarkdown: looksLikeMarkdown,
        escapeHtml: escapeHtml
    };

    function renderAll(root) {
        (root || document).querySelectorAll('pre.js-markdown-text').forEach(function (pre) {
            if (pre.dataset.mdRendered === 'true') {
                return;
            }
            var text = pre.textContent || '';
            var name = pre.getAttribute('data-attachment-name') || '';
            if (!looksLikeMarkdown(text, pre.getAttribute('data-attachment-type') || '', name)) {
                return;
            }
            var html = markdownToHtml(text);
            if (!html.trim()) {
                return;
            }
            var rendered = document.createElement('div');
            rendered.className = 'md-rendered';
            rendered.innerHTML = html;
            pre.dataset.mdRendered = 'true';
            pre.hidden = true;
            pre.insertAdjacentElement('afterend', rendered);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            renderAll();
        });
    } else {
        renderAll();
    }
})();
