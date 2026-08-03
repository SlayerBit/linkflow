/**
 * handleSummary helper: writes JSON + a self-contained HTML report.
 * No CDN dependency — works offline after a run.
 */

export function buildSummary(data, opts = {}) {
  const stamp = opts.stamp || new Date().toISOString().replace(/[:.]/g, '-');
  const scenario = opts.scenario || 'run';
  const dir = opts.dir || 'performance/reports';

  const jsonPath = `${dir}/${scenario}-${stamp}.json`;
  const htmlPath = `${dir}/${scenario}-${stamp}.html`;

  return {
    stdout: textSummary(data, scenario),
    [jsonPath]: JSON.stringify(data, null, 2),
    [htmlPath]: htmlReport(data, scenario, stamp),
  };
}

function textSummary(data, scenario) {
  const m = data.metrics || {};
  const dur = m.http_req_duration;
  const failed = m.http_req_failed;
  const checks = m.checks;
  const lines = [
    '',
    `=== LinkFlow k6 summary: ${scenario} ===`,
    `  http_req_duration p95: ${pct(dur, 'p(95)')}  p99: ${pct(dur, 'p(99)')}  avg: ${pct(dur, 'avg')}`,
    `  http_req_failed: ${rate(failed)}`,
    `  checks: ${rate(checks)}`,
    '  (Thresholds are regression gates for this run — not published product SLOs.)',
    '',
  ];
  return lines.join('\n');
}

function pct(metric, key) {
  if (!metric || !metric.values) return 'n/a';
  const v = metric.values[key];
  return v == null ? 'n/a' : `${v.toFixed(2)}ms`;
}

function rate(metric) {
  if (!metric || !metric.values) return 'n/a';
  const v = metric.values.rate;
  return v == null ? 'n/a' : `${(v * 100).toFixed(2)}%`;
}

function htmlReport(data, scenario, stamp) {
  const m = data.metrics || {};
  const rows = Object.keys(m)
    .sort()
    .map((name) => {
      const vals = m[name].values || {};
      const cells = Object.keys(vals)
        .map((k) => `<td><code>${k}</code>=${formatVal(vals[k])}</td>`)
        .join('');
      return `<tr><th>${escapeHtml(name)}</th>${cells}</tr>`;
    })
    .join('\n');

  const thresholds = (data.root_group && data.root_group.checks) || [];
  void thresholds;

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <title>LinkFlow k6 — ${escapeHtml(scenario)}</title>
  <style>
    body { font-family: ui-sans-serif, system-ui, sans-serif; margin: 2rem; color: #122; background: #f7f8fa; }
    h1 { font-size: 1.4rem; }
    .note { color: #456; margin-bottom: 1.5rem; max-width: 52rem; }
    table { border-collapse: collapse; width: 100%; background: #fff; }
    th, td { border: 1px solid #dde; padding: 0.4rem 0.6rem; text-align: left; font-size: 0.85rem; vertical-align: top; }
    th { background: #eef1f6; white-space: nowrap; }
    code { font-size: 0.8rem; }
  </style>
</head>
<body>
  <h1>LinkFlow k6 report — ${escapeHtml(scenario)}</h1>
  <p class="note">Generated ${escapeHtml(stamp)}. Metric values and threshold outcomes are
  results for <em>this</em> run only. Do not treat default threshold numbers in the suite as
  published LinkFlow SLOs or capacity claims.</p>
  <table>
    <tbody>
      ${rows}
    </tbody>
  </table>
</body>
</html>`;
}

function formatVal(v) {
  if (typeof v === 'number') {
    return Number.isInteger(v) ? String(v) : v.toFixed(4);
  }
  return escapeHtml(String(v));
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
