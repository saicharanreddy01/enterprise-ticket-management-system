/**
 * dashboard-reports.js — Dashboard charts & Reports page
 * =============================================================
 * Depends on: core.js. Uses the Chart.js library (loaded via CDN in index.html).
 *
 * Covers two related but distinct views:
 *   1. The Dashboard Overview tabbed chart panel (status/category/priority charts,
 *      SLA metric, per-developer widget, volume trend).
 *   2. The Reports page (Agent Performance table, CSAT summary card, date-range
 *      presets).
 */

/**
 * Charts previously used a hardcoded Google-Material color set (var(--g-blue),
 * var(--g-green), var(--g-red)...) left over from before the TrackTicket brand existed.
 * This pulls the real, current brand tokens from CSS at render time instead,
 * so charts always match whatever the stylesheet defines — one palette,
 * not two.
 */
function brandColor(varName, fallback) {
    const v = getComputedStyle(document.documentElement).getPropertyValue(varName).trim();
    return v || fallback;
}

async function loadCsatSummary() {
    const el = document.getElementById('csatSummaryText');
    if (!el) return;
    try {
        const res = await fetchWithAuth('/api/csat/summary', { headers: authHeaders() });
        if (!res.ok) { el.textContent = 'Unable to load CSAT data.'; return; }
        const data = await res.json();
        el.textContent = data.averageRating != null
            ? `${data.averageRating} / 5 average, from ${data.totalRatings} rating(s).`
            : `No ratings submitted yet (${data.totalRatings} sent, 0 responded).`;
    } catch (e) {
        console.error(e);
        el.textContent = 'Unable to load CSAT data.';
    }
}

async function loadAgentPerformance() {
    const tbody = document.getElementById('agentPerfBody');
    if (!tbody) return;
    renderSkeletonRows(tbody, 6, 4, ['70%', '30%', '30%', '40%', '40%', '30%']);

    const from = document.getElementById('reportFrom')?.value;
    const to   = document.getElementById('reportTo')?.value;

    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to)   params.set('to', to);
    const query = params.toString() ? `?${params.toString()}` : '';

    updateReportRangeLabel(from, to);

    try {
        const res = await fetchWithAuth(`/api/reports/agent-performance${query}`, {
            headers: authHeaders()
        });
        if (!res.ok) return;
        const agents = await res.json();

        if (!agents.length) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--g-text-muted); padding:32px;">No agent data yet. Assign tickets to agents to see metrics.</td></tr>';
            return;
        }

        tbody.innerHTML = agents.map(a => {
            const slaColor = a.slaComplianceRate >= 80 ? 'var(--g-green)'
                : a.slaComplianceRate >= 60 ? 'var(--g-yellow)'
                    : 'var(--g-red)';
            const resolutionRate = a.totalAssigned > 0
                ? Math.round((a.totalResolved / a.totalAssigned) * 100) : 0;

            return `
                <tr>
                    <td>
                        <div style="display:flex; align-items:center; gap:10px;">
                            <div style="width:32px; height:32px; border-radius:50%; background:var(--g-blue-light);
                                        color:var(--g-blue); display:flex; align-items:center; justify-content:center;
                                        font-weight:600; font-size:13px; flex-shrink:0;">
                                ${escapeHtml(a.agent[0].toUpperCase())}
                            </div>
                            <span style="font-weight:500;">${escapeHtml(a.agent)}</span>
                        </div>
                    </td>
                    <td style="text-align:center; font-weight:600; color:var(--g-blue);">${a.totalAssigned}</td>
                    <td style="text-align:center;">
                        <span style="font-weight:600; color:var(--g-green);">${a.totalResolved}</span>
                        <span style="font-size:11px; color:var(--g-text-muted);"> (${resolutionRate}%)</span>
                    </td>
                    <td style="text-align:center; color:var(--g-text-main);">
                        ${a.avgResolutionHours != null ? a.avgResolutionHours + 'h' : '—'}
                    </td>
                    <td style="text-align:center; color:var(--g-text-main);">
                        ${a.avgFirstResponseHours != null ? a.avgFirstResponseHours + 'h' : '—'}
                    </td>
                    <td style="text-align:center;">
                        <span style="font-weight:600; color:${slaColor};">${a.slaComplianceRate}%</span>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        console.error('Failed to load agent performance:', e);
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--g-red); padding:32px;">Failed to load report.</td></tr>';
    }
}

function toDateInputValue(date) {
    // yyyy-MM-dd, in local time (not UTC) so it matches what <input type="date"> expects
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

function setReportRangePreset(days) {
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - (days - 1));

    document.getElementById('reportFrom').value = toDateInputValue(from);
    document.getElementById('reportTo').value   = toDateInputValue(to);
    activeRangePreset = days;
    updateReportPresetButtons();
    loadAgentPerformance();
}

function clearReportRange() {
    document.getElementById('reportFrom').value = '';
    document.getElementById('reportTo').value   = '';
    activeRangePreset = 'all';
    updateReportPresetButtons();
    loadAgentPerformance();
}

// Gives the selected range a persistent highlight instead of only a hover
// state — previously there was no visual indication of which preset (if
// any) was actually currently applied.
function updateReportPresetButtons() {
    const map = { 7: 'presetBtn7', 30: 'presetBtn30', all: 'presetBtnAll' };
    Object.values(map).forEach(id => document.getElementById(id)?.classList.remove('active'));
    const activeId = map[activeRangePreset];
    if (activeId) document.getElementById(activeId)?.classList.add('active');
}

function updateReportRangeLabel(from, to) {
    const label = document.getElementById('reportRangeLabel');
    if (!label) return;
    if (!from && !to) {
        label.innerText = 'Showing: all time';
    } else if (from && to) {
        label.innerText = `Showing: ${from} to ${to}`;
    } else if (from) {
        label.innerText = `Showing: from ${from}`;
    } else {
        label.innerText = `Showing: through ${to}`;
    }
}

async function loadVolumeTrend(days) {
    activeVolumeDays = days || 30;

    // Highlight active segment
    ['vol7', 'vol30', 'vol90'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.classList.toggle('active', id === 'vol' + activeVolumeDays);
    });

    try {
        const res = await fetchWithAuth(`/api/reports/volume?days=${activeVolumeDays}`, {
            headers: authHeaders()
        });
        if (!res.ok) return;
        const data = await res.json();

        const labels = data.map(d => {
            const date = new Date(d.date);
            return date.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' });
        });
        const counts = data.map(d => d.count);

        const ctx = document.getElementById('volumeChart');
        if (!ctx) return;
        if (volumeChartInstance) volumeChartInstance.destroy();

        const lineColor = brandColor('--g-blue-dark', '#023e8a');
        const tickColor = brandColor('--g-text-muted', '#3D5170');
        const gridColor = brandColor('--g-border', '#DCEEF5');

        volumeChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: 'Tickets raised',
                    data: counts,
                    borderColor: lineColor,
                    backgroundColor: lineColor + '15',
                    borderWidth: 2,
                    pointBackgroundColor: lineColor,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    tension: 0.3,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: ctx => ` ${ctx.raw} ticket${ctx.raw !== 1 ? 's' : ''}`
                        }
                    }
                },
                scales: {
                    x: {
                        ticks: { font: { family: 'Inter', size: 11 }, color: tickColor },
                        grid: { display: false },
                        border: { display: false }
                    },
                    y: {
                        beginAtZero: true,
                        ticks: {
                            font: { family: 'Inter', size: 11 },
                            color: tickColor,
                            precision: 0
                        },
                        grid: { color: gridColor },
                        border: { display: false }
                    }
                }
            }
        });
    } catch (e) {
        console.error('Volume trend error:', e);
    }
}

// --- 3a. Dashboard Charts (tabbed panel) ---
function renderDashboardCharts(summary) {
    lastDashboardSummary = summary;
    renderSlaMetric(summary.slaCompliancePct);
    switchChartTab(activeChartTab, null, summary);
}

function renderSlaMetric(slaCompliancePct) {
    if (slaCompliancePct === null || slaCompliancePct === undefined) {
        document.getElementById('metricSla').innerText = 'N/A';
        return;
    }
    const pct = slaCompliancePct;
    document.getElementById('metricSla').innerText = `${pct}%`;
    const barColor = pct >= 80 ? brandColor('--viz-green', '#16A34A')
        : pct >= 60 ? brandColor('--viz-orange', '#F59E0B')
            : brandColor('--viz-red', '#E11D48');
    document.getElementById('slaProgressBar').style.width = `${pct}%`;
    document.getElementById('slaProgressBar').style.background = barColor;
    document.getElementById('metricSla').style.color = barColor;
}

async function renderDevPersonalWidget() {
    const widget = document.getElementById('devPersonalWidget');
    if (!widget || widget.style.display === 'none') return;

    try {
        const res = await fetchWithAuth('/api/reports/dashboard-summary/me', { headers: authHeaders() });
        if (!res.ok) return;
        const summary = await res.json();

        document.getElementById('devOpenCount').innerText     = summary.openCount;
        document.getElementById('devSlaRate').innerText        = summary.slaCompliancePct == null ? 'N/A' : summary.slaCompliancePct + '%';
        document.getElementById('devResolvedToday').innerText  = summary.resolvedToday;
    } catch (e) {
        console.error('Failed to load personal dashboard summary:', e);
    }
}

// Cached from the last /api/reports/dashboard-summary call, so switching
// chart tabs re-renders instantly without a network round-trip.
let lastDashboardSummary = null;

function switchChartTab(tab, el, summary) {
    // Update active tab highlight
    activeChartTab = tab;
    if (el) {
        document.querySelectorAll('.chart-tab').forEach(t => t.classList.remove('active'));
        el.classList.add('active');
    }
    const data = summary || lastDashboardSummary;
    if (!data) return;
    if (tab === 'status')    renderStatusChart(data.statusBreakdown);
    if (tab === 'category')  renderCategoryChart(data.categoryBreakdown);
    if (tab === 'priority')  renderPriorityChart(data.priorityBreakdown);
}

function buildChart(type, labels, values, colors, opts = {}) {
    const ctx = document.getElementById('mainChart');
    if (!ctx) return;
    if (mainChartInstance) mainChartInstance.destroy();

    const baseFont = { family: 'Inter', size: 12 };
    const borderRadius = type === 'bar' ? 6 : 0;
    const surfaceColor = brandColor('--g-surface', '#FFFFFF');
    const mutedColor   = brandColor('--g-text-muted', '#3D5170');
    const mainColor    = brandColor('--g-text-main', '#0A1128');
    const gridColor    = brandColor('--g-border', '#DCEEF5');

    mainChartInstance = new Chart(ctx, {
        type,
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor: type === 'doughnut' ? colors : colors.map(c => c + 'CC'),
                borderColor: type === 'doughnut' ? surfaceColor : colors,
                borderWidth: type === 'doughnut' ? 3 : 2,
                borderRadius: type !== 'doughnut' ? borderRadius : undefined,
                hoverOffset: type === 'doughnut' ? 6 : undefined,
                borderSkipped: false
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: type === 'doughnut' ? '68%' : undefined,
            indexAxis: opts.horizontal ? 'y' : 'x',
            plugins: {
                legend: {
                    display: type === 'doughnut',
                    position: 'right',
                    labels: {
                        font: baseFont,
                        color: mutedColor,
                        padding: 16,
                        usePointStyle: true,
                        pointStyleWidth: 8
                    }
                },
                tooltip: {
                    callbacks: {
                        label: ctx => ` ${ctx.label}: ${ctx.raw} ticket${ctx.raw !== 1 ? 's' : ''}`
                    }
                }
            },
            scales: type !== 'doughnut' ? {
                x: {
                    ticks: { font: baseFont, color: mutedColor, precision: 0 },
                    grid: { color: gridColor },
                    border: { display: false }
                },
                y: {
                    ticks: { font: baseFont, color: mainColor },
                    grid: { display: false },
                    border: { display: false }
                }
            } : {}
        }
    });
}

function showChartEmptyState(canvasId, message) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    canvas.style.display = 'none';
    let placeholder = canvas.parentElement.querySelector('.chart-empty-state');
    if (!placeholder) {
        placeholder = document.createElement('div');
        placeholder.className = 'chart-empty-state';
        canvas.parentElement.appendChild(placeholder);
    }
    placeholder.innerHTML = `
        <span class="material-symbols-rounded" aria-hidden="true">insights</span>
        <span>${message}</span>`;
    placeholder.style.display = 'flex';
}

function hideChartEmptyState(canvasId) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    canvas.style.display = 'block';
    const placeholder = canvas.parentElement.querySelector('.chart-empty-state');
    if (placeholder) placeholder.style.display = 'none';
}

// These three used to take a full array of tickets and count them client-side.
// They now take the already-aggregated counts object the backend computes via
// GROUP BY — same charts, but the payload is a handful of numbers instead of
// every ticket in the system, and it doesn't get slower as ticket volume grows.

function renderStatusChart(statusBreakdown) {
    const order  = ['OPEN', 'IN_PROGRESS', 'NEW', 'PENDING', 'REOPENED', 'RESOLVED', 'CLOSED'];
    // Vivid, clearly differentiated status palette. CLOSED — often the
    // largest slice — gets neutral slate rather than dark navy, so the whole
    // chart doesn't read as one dominant navy blob.
    const colors = {
        OPEN:         brandColor('--viz-blue', '#2563EB'),
        NEW:          brandColor('--viz-purple', '#7C3AED'),
        IN_PROGRESS:  brandColor('--viz-orange', '#F59E0B'),
        PENDING:      brandColor('--tm-signal', '#00b4d8'),
        REOPENED:     brandColor('--viz-red', '#E11D48'),
        RESOLVED:     brandColor('--viz-green', '#16A34A'),
        CLOSED:       brandColor('--viz-slate', '#64748B')
    };
    const counts = statusBreakdown || {};
    const active = order.filter(s => (counts[s] || 0) > 0);
    if (!active.length) { showChartEmptyState('mainChart', 'No tickets yet — raise one to see the status breakdown.'); return; }
    hideChartEmptyState('mainChart');
    buildChart('doughnut', active, active.map(s => counts[s]), active.map(s => colors[s]));
}

function renderCategoryChart(categoryBreakdown) {
    const sorted  = Object.entries(categoryBreakdown || {}).sort((a, b) => b[1] - a[1]);
    const labels  = sorted.map(([k]) => k);
    const values  = sorted.map(([, v]) => v);
    if (!labels.length) { showChartEmptyState('mainChart', 'No category data yet.'); return; }
    hideChartEmptyState('mainChart');
    // A varied, vivid qualitative palette — categories aren't ordered/sequential
    // data, so they read better as clearly distinct hues rather than shades
    // of one color.
    const palette = ['--viz-blue', '--viz-green', '--viz-orange', '--viz-red', '--viz-purple', '--tm-signal']
        .map(v => brandColor(v, '#2563EB'));
    buildChart('bar', labels, values, labels.map((_, i) => palette[i % palette.length]), { horizontal: true });
}

function renderPriorityChart(priorityBreakdown) {
    const counts = { HIGH: 0, MEDIUM: 0, LOW: 0, ...(priorityBreakdown || {}) };
    const labels = Object.keys(counts).filter(p => counts[p] > 0);
    if (!labels.length) { showChartEmptyState('mainChart', 'No priority data yet.'); return; }
    hideChartEmptyState('mainChart');
    const colors = {
        HIGH:   brandColor('--viz-red', '#E11D48'),
        MEDIUM: brandColor('--viz-orange', '#F59E0B'),
        LOW:    brandColor('--viz-green', '#16A34A')
    };
    buildChart('bar', labels, labels.map(p => counts[p]), labels.map(p => colors[p]));
}