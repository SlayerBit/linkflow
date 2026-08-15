function copyToClipboard(text, button) {
    navigator.clipboard.writeText(text).then(function () {
        if (button) {
            var original = button.innerHTML;
            button.innerHTML = '<i class="ti ti-check" aria-hidden="true"></i> Copied';
            setTimeout(function () {
                button.innerHTML = original;
            }, 2000);
        }
    });
}

function appendCell(row, text, className) {
    var cell = document.createElement('td');
    if (className) {
        cell.className = className;
    }
    cell.textContent = text != null && text !== '' ? text : '—';
    row.appendChild(cell);
    return cell;
}

function prefersReducedMotion() {
    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function chartDefaults() {
    if (typeof Chart === 'undefined') {
        return;
    }
    Chart.defaults.font.family = 'ui-sans-serif, system-ui, sans-serif';
    Chart.defaults.color = '#64748b';
    if (prefersReducedMotion()) {
        Chart.defaults.animation = false;
    }
}

function revealCharts() {
    document.querySelectorAll('[data-chart-skeleton]').forEach(function (el) {
        el.hidden = true;
    });
    document.querySelectorAll('[data-chart-canvas]').forEach(function (el) {
        el.hidden = false;
    });
}

async function fireRateLimitProbe(count, endpoint) {
    var tbody = document.getElementById('probe-results-body');
    var alert429 = document.getElementById('alert-429');
    tbody.innerHTML = '<tr><td colspan="6" class="text-muted">Firing requests...</td></tr>';
    alert429.classList.add('d-none');

    var csrfToken = document.querySelector('meta[name="_csrf"]');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (csrfToken && csrfHeader) {
        headers[csrfHeader.content] = csrfToken.content;
    }

    var url = '/tools/rate-limit/probe?n=' + encodeURIComponent(count) +
        '&endpoint=' + encodeURIComponent(endpoint);

    var response = await fetch(url, { headers: headers });
    if (!response.ok) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-danger">Probe failed (HTTP ' + response.status + ').</td></tr>';
        return;
    }

    var data = await response.json();
    tbody.innerHTML = '';

    data.results.forEach(function (row) {
        var tr = document.createElement('tr');
        var statusClass = row.status === 429 ? 'status-429' : (row.status >= 200 && row.status < 300 ? 'status-2xx' : '');
        tr.className = statusClass;

        appendCell(tr, String(row.requestNumber));

        var statusCell = document.createElement('td');
        var badge = document.createElement('span');
        badge.className = 'badge ' + (row.status === 429 ? 'bg-red' : 'bg-green');
        badge.textContent = String(row.status);
        statusCell.appendChild(badge);
        tr.appendChild(statusCell);

        appendCell(tr, row.limit);
        appendCell(tr, row.remaining);
        appendCell(tr, row.reset);
        appendCell(tr, row.message || '', 'text-muted');
        tbody.appendChild(tr);
    });

    if (data.has429) {
        alert429.classList.remove('d-none');
        document.getElementById('alert-429-message').textContent =
            data.message429 || 'Rate limit exceeded (HTTP 429).';
    }
}

document.addEventListener('DOMContentLoaded', function () {
    chartDefaults();
    revealCharts();

    document.querySelectorAll('.copy-btn[data-copy-url]').forEach(function (button) {
        button.addEventListener('click', function () {
            copyToClipboard(button.getAttribute('data-copy-url'), button);
        });
    });

    document.querySelectorAll('form').forEach(function (form) {
        form.querySelectorAll('.field-error').forEach(function (err) {
            var field = form.querySelector('[name="' + err.getAttribute('data-field') + '"]');
            if (field) {
                field.setAttribute('aria-invalid', 'true');
                if (err.id) {
                    field.setAttribute('aria-describedby', err.id);
                }
            }
        });
    });

    var probeBtn = document.getElementById('fire-probe-btn');
    if (probeBtn) {
        probeBtn.addEventListener('click', function () {
            probeBtn.disabled = true;
            var count = parseInt(document.getElementById('probe-count').value, 10) || 10;
            var endpoint = document.getElementById('probe-endpoint').value;
            fireRateLimitProbe(count, endpoint).finally(function () {
                probeBtn.disabled = false;
            });
        });
    }
});
