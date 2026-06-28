// --- 1. Auth & Security ---
function authHeaders() { return { 'Content-Type': 'application/json' }; }

function guardAuth() {
    if (!localStorage.getItem('jwt_username')) window.location.href = '/landing.html';
}

async function fetchWithAuth(url, options = {}) {
    // credentials: 'include' ensures cookies are sent on every request
    options.credentials = 'include';
    let response = await fetch(url, options);

    if (response.status === 401) {
        // Try silent token refresh — server reads cookie, sets new cookie
        const refreshResponse = await fetch('/api/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include'
        });
        if (refreshResponse.ok) {
            return await fetch(url, options);
        }
        logout();
    }
    return response;
}

function toggleMobileNav() {
    const nav     = document.querySelector('.md-nav-drawer');
    const overlay = document.getElementById('mobileOverlay');
    const isOpen  = nav.classList.contains('mobile-open');
    nav.classList.toggle('mobile-open', !isOpen);
    overlay.classList.toggle('visible', !isOpen);
}

async function logout() {
    try {
        // Server reads refresh token from cookie, revokes it, and clears both cookies
        await fetch('/api/auth/logout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include'
        });
    } catch (e) {
        console.error('Logout revocation failed:', e);
    }
    localStorage.clear();
    window.location.href = '/landing.html';
}

// --- 2. State & Init ---
let ticketIdPendingResolve = null;
let currentTickets = [];
let currentDetailTicketId = null;
let consoleCurrentPage = 0;
let allUsers = [];
let selectedTicketIds = new Set();
let consoleSearchQuery = '';
let mainChartInstance = null;
let volumeChartInstance = null;
let activeVolumeDays = 30;
let activeChartTab = 'status';

document.addEventListener('DOMContentLoaded', () => {
    guardAuth();

    // Setup Avatar
    const user = localStorage.getItem('jwt_username');
    if (user) document.getElementById('userAvatar').innerText = user.charAt(0).toUpperCase();

    // 4. Role-Based Access Control (Admin Reveal)
    const role = localStorage.getItem('jwt_role');
    if (role === 'ADMIN') {
        const adminSec = document.getElementById('adminNavSection');
        if(adminSec) adminSec.style.display = 'block';
    }
    if (role === 'ADMIN') {
        const reportsNav = document.getElementById('reportsNavItem');
        if (reportsNav) reportsNav.style.display = 'flex';
    }

    // Live Clock
    setInterval(() => {
        const d = new Date();
        document.getElementById('liveClock').innerText = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }, 1000);

    refreshDataPipeline();
    loadCategories();
    loadUsers();
    refreshNotifications();
    // Poll every 60 seconds — matches the SLA breach scheduler cadence so new breach
    // notifications appear in the bell within a minute of being written to the DB
    setInterval(refreshNotifications, 60000);
});

function switchView(viewId, el) {
    document.querySelectorAll('.app-view').forEach(v => v.classList.remove('active-view'));
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    document.getElementById(viewId).classList.add('active-view');
    if (el) {
        el.classList.add('active');
        // Extract text safely without the icon text
        const rawText = el.innerText.replace(/^[a-z_]+\s*/i, '').trim();
        document.getElementById('currentViewTitle').innerText = rawText || "Admin Panel";
    }
    // Close mobile nav when navigating
    if (window.innerWidth <= 768) {
        const nav = document.querySelector('.md-nav-drawer');
        const overlay = document.getElementById('mobileOverlay');
        nav.classList.remove('mobile-open');
        overlay.classList.remove('visible');
    }
    if (viewId === 'viewConsole') {
        loadConsolePage(0, '');
    }
    if (viewId === 'viewReports') {
        loadAgentPerformance();
        loadVolumeTrend(30);
    }
}

async function loadCategories() {
    const select = document.getElementById('category');
    try {
        const response = await fetchWithAuth('/api/categories', { headers: authHeaders() });
        if (response.ok) {
            const categories = await response.json();
            categories.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.name; // textContent, not innerHTML — same safety habit as the XSS fix
                select.appendChild(opt);
            });
        }
    } catch (e) { console.error("Failed to load categories:", e); }
}

async function loadUsers() {
    try {
        const res = await fetchWithAuth('/api/auth/users', { headers: authHeaders() });
        if (!res.ok) return;
        allUsers = await res.json();
    } catch (e) {
        console.error('Failed to load users:', e);
    }
}

async function loadAgentPerformance() {
    const tbody = document.getElementById('agentPerfBody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--g-text-muted); padding:32px;">Loading...</td></tr>';

    try {
        const res = await fetchWithAuth('/api/reports/agent-performance', {
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
                        ${a.avgResolutionHours > 0 ? a.avgResolutionHours + 'h' : '—'}
                    </td>
                    <td style="text-align:center; color:var(--g-text-main);">
                        ${a.avgFirstResponseHours > 0 ? a.avgFirstResponseHours + 'h' : '—'}
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

async function loadVolumeTrend(days) {
    activeVolumeDays = days || 30;

    // Highlight active button
    ['vol7', 'vol30', 'vol90'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.style.fontWeight = id === 'vol' + activeVolumeDays ? '700' : '400';
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

        volumeChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: 'Tickets raised',
                    data: counts,
                    borderColor: '#1A73E8',
                    backgroundColor: 'rgba(26,115,232,0.08)',
                    borderWidth: 2,
                    pointBackgroundColor: '#1A73E8',
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
                        ticks: { font: { family: 'Inter', size: 11 }, color: '#5F6368' },
                        grid: { display: false },
                        border: { display: false }
                    },
                    y: {
                        beginAtZero: true,
                        ticks: {
                            font: { family: 'Inter', size: 11 },
                            color: '#5F6368',
                            precision: 0
                        },
                        grid: { color: '#F1F3F4' },
                        border: { display: false }
                    }
                }
            }
        });
    } catch (e) {
        console.error('Volume trend error:', e);
    }
}

function populateAssignedAgentDropdown(currentAgent) {
    const select = document.getElementById('assignedAgentSelect');
    const label  = document.getElementById('assignedAgentLabel');
    if (!select || !label) return;

    const role = localStorage.getItem('jwt_role');

    if (role === 'ADMIN') {
        // Admin sees the dropdown to assign/reassign
        select.style.display = 'inline-block';
        label.style.display  = 'none';
        select.innerHTML = '<option value="">Unassigned</option>';
        allUsers.forEach(u => {
            const opt = document.createElement('option');
            opt.value = u.username;
            opt.textContent = u.username + ' (' + u.role + ')';
            if (u.username === currentAgent) opt.selected = true;
            select.appendChild(opt);
        });
    } else {
        // Developer sees read-only label
        select.style.display = 'none';
        label.style.display  = 'inline-block';
        label.textContent = currentAgent || 'Unassigned';
    }
}

async function reassignAgent(agent) {
    if (!currentDetailTicketId) return;
    try {
        await fetchWithAuth(`/api/tickets/${currentDetailTicketId}/assign`, {
            method: 'PUT',
            headers: authHeaders(),
            body: JSON.stringify({ assignedAgent: agent || null })
        });
        refreshDataPipeline();
        loadConsolePage(consoleCurrentPage, consoleSearchQuery);
    } catch (e) {
        console.error('Reassignment failed:', e);
    }
}

function updateSlaPauseButton(ticket) {
    const btn   = document.getElementById('slaPauseBtn');
    const icon  = document.getElementById('slaPauseIcon');
    const label = document.getElementById('slaPauseLabel');
    if (!btn) return;

    const role = localStorage.getItem('jwt_role');

    // Only show for ADMIN, and only when ticket is not already resolved/closed
    const inactiveStatuses = ['RESOLVED', 'CLOSED'];
    if (role !== 'ADMIN' || inactiveStatuses.includes(ticket.status)) {
        btn.style.display = 'none';
        return;
    }

    btn.style.display = 'inline-flex';

    if (ticket.status === 'PENDING') {
        // Currently paused — show Resume
        icon.innerText  = 'play_arrow';
        label.innerText = 'Resume SLA';
        btn.style.color = 'var(--g-green)';
        btn.style.borderColor = 'var(--g-green)';
    } else {
        // Currently running — show Pause
        icon.innerText  = 'pause';
        label.innerText = 'Pause SLA';
        btn.style.color = 'var(--g-text-muted)';
        btn.style.borderColor = 'var(--g-border)';
    }
}

async function toggleSlaPause() {
    if (!currentDetailTicketId) return;

    const ticket = currentTickets.find(t => t.id === currentDetailTicketId);
    if (!ticket) return;

    const newStatus = ticket.status === 'PENDING' ? 'IN_PROGRESS' : 'PENDING';

    try {
        await fetchWithAuth(`/api/tickets/${currentDetailTicketId}`, {
            method: 'PUT',
            headers: authHeaders(),
            body: JSON.stringify({
                title:       ticket.title,
                description: ticket.description,
                priority:    ticket.priority,
                status:      newStatus
            })
        });
        await refreshDataPipeline();
        // Re-open the detail modal with fresh data to reflect the new state
        openTicketDetail(currentDetailTicketId);
    } catch (e) {
        console.error('SLA pause toggle failed:', e);
    }
}

// --- 3. Pipeline & Table Rendering ---
async function refreshDataPipeline() {
    try {
        const response = await fetchWithAuth('/api/tickets', { headers: authHeaders() });
        if (response.ok) {
            const rawData = await response.json();
            const tickets = Array.isArray(rawData) ? rawData : (rawData.content || []);
            currentTickets = tickets;

            document.getElementById('metricTotal').innerText = tickets.length;
            document.getElementById('metricOpen').innerText = tickets.filter(t => ['NEW', 'OPEN', 'IN_PROGRESS'].includes(t.status)).length;
            document.getElementById('metricResolved').innerText = tickets.filter(t => ['RESOLVED', 'CLOSED'].includes(t.status)).length;

            renderDashboardCharts(tickets);
        }
    } catch (e) { console.error("Pipeline Sync Error:", e); }
}
function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function renderTable(tickets) {
    const tbody = document.getElementById('ticketTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    tickets.forEach(t => {
        let statusClass = 'ticket-chip';
        if (['NEW', 'OPEN'].includes(t.status)) statusClass += ' chip-new';
        else if (t.status === 'IN_PROGRESS') statusClass += ' chip-progress';
        else if (t.status === 'RESOLVED') statusClass += ' chip-resolved';

        let slaText = '<span style="color:var(--g-text-muted);">No SLA Set</span>';
        if (t.slaBreached) slaText = '<span style="color:var(--g-red); font-weight:600;">BREACHED</span>';
        else if (t.slaDueDate) slaText = new Date(t.slaDueDate).toLocaleString([], {month:'short', day:'numeric', hour:'2-digit', minute:'2-digit'});

        let categoryName = t.category && t.category.name ? escapeHtml(t.category.name) : 'Uncategorized';
        let priorityStyle = t.priority === 'HIGH' ? 'color: var(--g-red); font-weight: 600;' : 'color: var(--g-text-muted);';

        let actionBtn = `<span style="color: var(--g-text-muted); font-size:12px;">Closed</span>`;
        if (['NEW', 'OPEN'].includes(t.status)) {
            actionBtn = `<button class="g-btn g-btn-text" style="padding: 0 12px; height: 28px; font-size: 12px;" onclick="startProgress(${t.id})">Ack</button>`;
        } else if (t.status === 'IN_PROGRESS' || t.status === 'REOPENED') {
            // REOPENED means the customer rejected the fix — it needs re-resolving, not "Closed"
            actionBtn = `<button class="g-btn g-btn-primary" style="padding: 0 12px; height: 28px; font-size: 12px;" onclick="prepResolve(${t.id})">Resolve</button>`;
        } else if (t.status === 'PENDING') {
            actionBtn = `<span style="color: var(--g-text-muted); font-size:12px;">Awaiting Response</span>`;
        } else if (t.status === 'RESOLVED') {
            actionBtn = `<span style="color: var(--g-blue); font-size:12px; font-weight:600;">Resolved</span>`;
        }

        // Build Table Row
        const tr = document.createElement('tr');
        tr.className = 'ticket-row'; // Tag for search filter
        tr.innerHTML = `
            <td style="width:40px;">
                <input type="checkbox" class="ticket-checkbox" data-id="${t.id}"
                       style="width:16px; height:16px; cursor:pointer;"
                       onchange="handleTicketCheckbox(this)"
                       ${selectedTicketIds.has(t.id) ? 'checked' : ''}>
            </td>
            <td style="color: var(--g-blue); font-weight: 500; cursor: pointer;" onclick="openTicketDetail(${t.id})">#${t.id}</td>
            <td style="font-weight: 500; color: var(--g-text-main); max-width: 300px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${escapeHtml(t.title)}</td>
            <td><span style="font-size: 12px; border: 1px solid var(--g-border); padding: 2px 6px; border-radius: 4px; color: var(--g-text-muted);">${categoryName}</span></td>
            <td style="${priorityStyle}">${t.priority}</td>
            <td><span class="${statusClass}">${t.status}</span></td>
            <td>${slaText}</td>
            <td style="text-align: right;">${actionBtn}</td>
        `;
        tbody.appendChild(tr);
    });
}

// --- 3a. Dashboard Charts (tabbed panel) ---
function renderDashboardCharts(tickets) {
    renderSlaMetric(tickets);
    switchChartTab(activeChartTab, null, tickets);
}

function renderSlaMetric(tickets) {
    const withDeadline = tickets.filter(t => t.slaDueDate);
    if (withDeadline.length === 0) {
        document.getElementById('metricSla').innerText = 'N/A';
        return;
    }
    const compliant = withDeadline.filter(t => !t.slaBreached).length;
    const pct = Math.round((compliant / withDeadline.length) * 100);
    document.getElementById('metricSla').innerText = `${pct}%`;
    const barColor = pct >= 80 ? '#1E8E3E' : pct >= 60 ? '#F9AB00' : '#D93025';
    document.getElementById('slaProgressBar').style.width = `${pct}%`;
    document.getElementById('slaProgressBar').style.background = barColor;
    document.getElementById('metricSla').style.color = barColor;
}

function switchChartTab(tab, el, tickets) {
    // Update active tab highlight
    activeChartTab = tab;
    if (el) {
        document.querySelectorAll('.chart-tab').forEach(t => t.classList.remove('active'));
        el.classList.add('active');
    }
    // Use live data or fall back to currentTickets if called from a tab click
    const data = tickets || currentTickets;
    if (tab === 'status')    renderStatusChart(data);
    if (tab === 'category')  renderCategoryChart(data);
    if (tab === 'priority')  renderPriorityChart(data);
}

function buildChart(type, labels, values, colors, opts = {}) {
    const ctx = document.getElementById('mainChart');
    if (!ctx) return;
    if (mainChartInstance) mainChartInstance.destroy();

    const baseFont = { family: 'Inter', size: 12 };
    const borderRadius = type === 'bar' ? 6 : 0;

    mainChartInstance = new Chart(ctx, {
        type,
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor: type === 'doughnut' ? colors : colors.map(c => c + 'CC'),
                borderColor: type === 'doughnut' ? '#FFFFFF' : colors,
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
                        color: '#5F6368',
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
                    ticks: { font: baseFont, color: '#5F6368', precision: 0 },
                    grid: { color: '#F1F3F4' },
                    border: { display: false }
                },
                y: {
                    ticks: { font: baseFont, color: '#202124' },
                    grid: { display: false },
                    border: { display: false }
                }
            } : {}
        }
    });
}

function renderStatusChart(tickets) {
    const order  = ['OPEN', 'IN_PROGRESS', 'NEW', 'PENDING', 'REOPENED', 'RESOLVED', 'CLOSED'];
    const colors = { OPEN:'#4285F4', IN_PROGRESS:'#F9AB00', NEW:'#1A73E8',
        PENDING:'#FBBC04', REOPENED:'#D93025', RESOLVED:'#1E8E3E', CLOSED:'#137333' };
    const counts = {};
    order.forEach(s => counts[s] = 0);
    tickets.forEach(t => { if (counts[t.status] !== undefined) counts[t.status]++; });
    const active = order.filter(s => counts[s] > 0);
    if (!active.length) return;
    buildChart('doughnut', active, active.map(s => counts[s]), active.map(s => colors[s]));
}

function renderCategoryChart(tickets) {
    const counts = {};
    tickets.forEach(t => {
        const name = t.category ? t.category.name : 'Uncategorized';
        counts[name] = (counts[name] || 0) + 1;
    });
    const sorted  = Object.entries(counts).sort((a, b) => b[1] - a[1]);
    const labels  = sorted.map(([k]) => k);
    const values  = sorted.map(([, v]) => v);
    const palette = ['#1A73E8','#34A853','#FBBC04','#EA4335','#9334E6','#00ACC1'];
    buildChart('bar', labels, values, labels.map((_, i) => palette[i % palette.length]), { horizontal: true });
}

function renderPriorityChart(tickets) {
    const counts = { HIGH: 0, MEDIUM: 0, LOW: 0 };
    tickets.forEach(t => { if (counts[t.priority] !== undefined) counts[t.priority]++; });
    const labels = Object.keys(counts).filter(p => counts[p] > 0);
    const colors = { HIGH: '#D93025', MEDIUM: '#F9AB00', LOW: '#1E8E3E' };
    buildChart('bar', labels, labels.map(p => counts[p]), labels.map(p => colors[p]));
}

// --- 3b. Ticket Detail & Comments ---
async function openTicketDetail(id) {
    try {
        const response = await fetchWithAuth(`/api/tickets/${id}`, { headers: authHeaders() });
        if (!response.ok) return;
        const ticket = await response.json();
        currentDetailTicketId = ticket.id;

        document.getElementById('detailTitle').innerText = ticket.title;
        document.getElementById('detailSubId').innerText = `Ticket #${ticket.id} · Raised by ${ticket.raisedBy || 'Unknown'}`;

        const statusChip = document.getElementById('detailStatusChip');
        statusChip.className = 'ticket-chip';
        if (['NEW', 'OPEN'].includes(ticket.status)) statusChip.className += ' chip-new';
        else if (ticket.status === 'IN_PROGRESS') statusChip.className += ' chip-progress';
        else if (ticket.status === 'RESOLVED') statusChip.className += ' chip-resolved';
        statusChip.innerText = ticket.status;

        const priorityChip = document.getElementById('detailPriorityChip');
        priorityChip.className = 'ticket-chip' + (ticket.priority === 'HIGH' ? ' chip-new' : '');
        priorityChip.innerText = ticket.priority;

        document.getElementById('detailCategoryChip').innerText = ticket.category ? ticket.category.name : 'Uncategorized';
        document.getElementById('detailDescription').innerText = ticket.description;

        renderComments(ticket.comments || []);
        loadAttachments(ticket.id);
        loadLinkedTickets(ticket.id);
        populateAssignedAgentDropdown(ticket.assignedAgent);
        updateSlaPauseButton(ticket);
        switchDetailTab('comments');
        document.getElementById('ticketDetailModal').showModal();
    } catch (e) {
        console.error('Failed to load ticket details:', e);
    }
}

function renderComments(comments) {
    const list = document.getElementById('detailCommentsList');
    list.innerHTML = '';
    if (comments.length === 0) {
        list.innerHTML = '<p style="color: var(--g-text-muted); font-size: 13px; text-align: center; padding: 24px 0;">No comments yet.</p>';
        return;
    }
    // Oldest first, like a real conversation thread
    const sorted = [...comments].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    sorted.forEach((c, i) => {
        const initial = (c.authorUsername || '?').charAt(0).toUpperCase();
        const time = new Date(c.createdAt).toLocaleString([], {month:'short', day:'numeric', hour:'2-digit', minute:'2-digit'});

        // Internal notes get an amber avatar + a left accent bar + a subtle tint —
        // public replies stay flat and neutral, like a Gmail thread
        const avatarBg = c.isInternal ? '#FEF7E0' : 'var(--g-blue-light)';
        const avatarColor = c.isInternal ? '#B06000' : 'var(--g-blue)';
        const rowStyle = c.isInternal
            ? 'background-color: #FFFBEA; border-left: 3px solid var(--g-yellow); border-radius: 0 8px 8px 0;'
            : '';
        const isLast = i === sorted.length - 1;

        const row = document.createElement('div');
        row.style.cssText = `display: flex; gap: 12px; padding: 12px; ${rowStyle} ${!isLast && !c.isInternal ? 'border-bottom: 1px solid var(--g-border);' : ''}`;

        const internalBadge = c.isInternal
            ? `<span style="display:inline-flex; align-items:center; gap:2px; font-size:11px; font-weight:600; color:#B06000; background:#FEF1C8; padding:2px 8px; border-radius:100px; margin-left:8px;"><span class="material-symbols-rounded" style="font-size:13px;">lock</span>Internal</span>`
            : '';

        row.innerHTML = `
            <div style="width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0; background: ${avatarBg}; color: ${avatarColor}; display: flex; align-items: center; justify-content: center; font-weight: 500; font-size: 13px;">${escapeHtml(initial)}</div>
            <div style="flex: 1; min-width: 0;">
                <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 8px;">
                    <span style="font-size: 13px;"><strong>${escapeHtml(c.authorUsername)}</strong>${internalBadge}</span>
                    <span style="font-size: 11px; color: var(--g-text-muted); white-space: nowrap;">${time}</span>
                </div>
                <p style="margin: 4px 0 0 0; font-size: 13px; line-height: 1.5; white-space: pre-wrap; color: var(--g-text-main);">${escapeHtml(c.content)}</p>
            </div>
        `;
        list.appendChild(row);
    });
}

async function submitComment() {
    if (!currentDetailTicketId) return;
    const content = document.getElementById('newCommentContent').value.trim();
    if (!content) return;
    const isInternal = document.getElementById('newCommentInternal').checked;

    await fetchWithAuth(`/api/tickets/${currentDetailTicketId}/comments`, {
        method: 'POST', headers: authHeaders(),
        body: JSON.stringify({ content, isInternal })
    });

    document.getElementById('newCommentContent').value = '';
    document.getElementById('newCommentInternal').checked = false;
    openTicketDetail(currentDetailTicketId); // refresh the thread with the new comment
}

async function loadAttachments(ticketId) {
    const list = document.getElementById('attachmentList');
    if (!list) return;

    try {
        const res = await fetchWithAuth(`/api/tickets/${ticketId}/attachments`, {
            headers: authHeaders()
        });
        const attachments = await res.json();

        if (!attachments.length) {
            list.innerHTML = '<span style="font-size:13px; color:var(--g-text-muted);">No attachments yet.</span>';
            return;
        }

        list.innerHTML = attachments.map(a => `
            <div style="display:flex; align-items:center; justify-content:space-between;
                        padding: 8px 12px; background:var(--g-background);
                        border:1px solid var(--g-border); border-radius:8px;">
                <div style="display:flex; align-items:center; gap:10px; min-width:0;">
                    <span class="material-symbols-rounded" style="font-size:18px; color:var(--g-blue); flex-shrink:0;">attach_file</span>
                    <div style="min-width:0;">
                        <div style="font-size:13px; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                            ${escapeHtml(a.originalFilename)}
                        </div>
                        <div style="font-size:11px; color:var(--g-text-muted);">
                            ${formatFileSize(a.fileSize)} · ${a.uploadedBy} · ${formatHistoryTimestamp(a.uploadedAt)}
                        </div>
                    </div>
                </div>
                <a href="/api/tickets/${a.ticketId}/attachments/${a.id}/download"
                   style="color:var(--g-blue); font-size:12px; font-weight:500; text-decoration:none;
                          white-space:nowrap; margin-left:12px; flex-shrink:0;">
                    Download
                </a>
            </div>
        `).join('');
    } catch (e) {
        console.error('Failed to load attachments:', e);
    }
}

function showLinkForm() {
    document.getElementById('linkForm').style.display = 'block';
    document.getElementById('linkTargetId').focus();
}

function hideLinkForm() {
    document.getElementById('linkForm').style.display = 'none';
    document.getElementById('linkTargetId').value = '';
    document.getElementById('linkError').style.display = 'none';
}

async function loadLinkedTickets(ticketId) {
    const list = document.getElementById('linkedTicketsList');
    if (!list) return;

    try {
        const res = await fetchWithAuth(`/api/tickets/${ticketId}/links`, {
            headers: authHeaders()
        });
        const links = await res.json();

        if (!links.length) {
            list.innerHTML = '<span style="font-size:13px; color:var(--g-text-muted);">No linked tickets.</span>';
            return;
        }

        const statusColors = {
            NEW: 'var(--g-red)', OPEN: 'var(--g-red)', IN_PROGRESS: 'var(--g-blue)',
            RESOLVED: 'var(--g-green)', CLOSED: 'var(--g-text-muted)', PENDING: 'var(--g-yellow)'
        };

        list.innerHTML = links.map(l => `
            <div style="display:flex; align-items:center; justify-content:space-between;
                        padding:8px 12px; background:var(--g-background);
                        border:1px solid var(--g-border); border-radius:8px;">
                <div style="display:flex; align-items:center; gap:8px; min-width:0;">
                    <span style="font-size:11px; color:var(--g-text-muted); white-space:nowrap;">
                        ${escapeHtml(l.label)}
                    </span>
                    <span style="color:var(--g-blue); font-weight:500; font-size:13px; cursor:pointer; white-space:nowrap;"
                          onclick="openTicketDetail(${l.otherTicketId})">
                        #${l.otherTicketId}
                    </span>
                    <span style="font-size:13px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                        ${escapeHtml(l.otherTicketTitle)}
                    </span>
                    <span style="font-size:11px; font-weight:600; color:${statusColors[l.otherStatus] || 'var(--g-text-muted)'}; white-space:nowrap;">
                        ${l.otherStatus}
                    </span>
                </div>
                <button onclick="removeLink(${l.linkId})"
                        style="background:none; border:none; cursor:pointer; color:var(--g-text-muted);
                               padding:0 4px; flex-shrink:0;" title="Remove link">
                    <span class="material-symbols-rounded" style="font-size:16px;">link_off</span>
                </button>
            </div>
        `).join('');
    } catch (e) {
        console.error('Failed to load links:', e);
    }
}

async function submitLink() {
    if (!currentDetailTicketId) return;
    const targetId = document.getElementById('linkTargetId').value.trim();
    const linkType = document.getElementById('linkTypeSelect').value;
    const errorDiv = document.getElementById('linkError');

    if (!targetId) {
        errorDiv.innerText = 'Enter a target ticket ID.';
        errorDiv.style.display = 'block';
        return;
    }

    try {
        const res = await fetchWithAuth(`/api/tickets/${currentDetailTicketId}/links`, {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ targetTicketId: targetId, linkType })
        });

        if (res.ok) {
            hideLinkForm();
            loadLinkedTickets(currentDetailTicketId);
        } else {
            const err = await res.json();
            errorDiv.innerText = err.error || 'Failed to create link.';
            errorDiv.style.display = 'block';
        }
    } catch (e) {
        errorDiv.innerText = 'Network error.';
        errorDiv.style.display = 'block';
    }
}

async function removeLink(linkId) {
    if (!currentDetailTicketId) return;
    try {
        await fetchWithAuth(`/api/tickets/${currentDetailTicketId}/links/${linkId}`, {
            method: 'DELETE',
            headers: authHeaders()
        });
        loadLinkedTickets(currentDetailTicketId);
    } catch (e) {
        console.error('Failed to remove link:', e);
    }
}

async function uploadAttachment(input) {
    if (!input.files.length || !currentDetailTicketId) return;
    const file = input.files[0];

    const formData = new FormData();
    formData.append('file', file);

    try {
        const res = await fetchWithAuth(`/api/tickets/${currentDetailTicketId}/attachments`, {
            method: 'POST',
            credentials: 'include',
            body: formData
            // No Content-Type header — browser sets it automatically with boundary for multipart
        });

        if (res.ok) {
            loadAttachments(currentDetailTicketId);
        } else {
            const err = await res.json();
            alert(err.error || 'Upload failed.');
        }
    } catch (e) {
        console.error('Upload error:', e);
    }

    // Reset input so the same file can be uploaded again if needed
    input.value = '';
}

function formatFileSize(bytes) {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// --- 5. Notification Bell ---
let notifPanelOpen = false;

async function refreshNotifications() {
    try {
        const response = await fetchWithAuth('/api/notifications', { headers: authHeaders() });
        if (!response.ok) return;
        const notifications = await response.json();

        const unread = notifications.filter(n => !n.read);
        const badge = document.getElementById('notifBadge');
        if (unread.length > 0) {
            badge.style.display = 'block';
            badge.innerText = unread.length > 9 ? '9+' : unread.length;
        } else {
            badge.style.display = 'none';
        }

        renderNotificationList(notifications);
    } catch (e) { console.error('Notification fetch error:', e); }
}

function renderNotificationList(notifications) {
    const list = document.getElementById('notifList');
    if (!list) return;

    if (notifications.length === 0) {
        list.innerHTML = '<p style="text-align:center; color:var(--g-text-muted); font-size:13px; padding:32px 0;">You\'re all caught up!</p>';
        return;
    }

    list.innerHTML = '';
    notifications.forEach(n => {
        const row = document.createElement('div');
        row.className = 'notif-row' + (!n.read ? ' unread' : '');

        // Icon per notification type — uses the same Material Symbols font already in the page
        const iconMap = {
            TICKET_CREATED:   { icon: 'confirmation_number', color: 'var(--g-blue)' },
            SLA_WARNING:      { icon: 'schedule',             color: 'var(--g-yellow)' },
            SLA_CRITICAL:     { icon: 'alarm',                color: '#E37400' },
            SLA_BREACHED:     { icon: 'warning',              color: 'var(--g-red)' },
            TICKET_REOPENED:  { icon: 'refresh',              color: 'var(--g-yellow)' },
            TICKET_AUTO_CLOSED: { icon: 'lock',               color: 'var(--g-text-muted)' }
        };
        const { icon, color } = iconMap[n.type] || { icon: 'info', color: 'var(--g-text-muted)' };
        const time = new Date(n.createdAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

        row.innerHTML = `
            <div style="width:36px; height:36px; border-radius:50%; background:${color}1A; flex-shrink:0;
                        display:flex; align-items:center; justify-content:center;">
                <span class="material-symbols-rounded" style="font-size:18px; color:${color};">${icon}</span>
            </div>
            <div style="flex:1; min-width:0;">
                <p style="margin:0 0 2px 0; font-size:13px; line-height:1.4; color:var(--g-text-main);">${escapeHtml(n.message)}</p>
                <span style="font-size:11px; color:var(--g-text-muted);">${time}</span>
            </div>
            ${!n.read ? '<div style="width:8px;height:8px;border-radius:50%;background:var(--g-blue);flex-shrink:0;margin-top:6px;"></div>' : ''}
        `;
        list.appendChild(row);
    });
}

function toggleNotificationPanel() {
    const panel = document.getElementById('notifPanel');
    notifPanelOpen = !notifPanelOpen;
    panel.style.display = notifPanelOpen ? 'block' : 'none';
}

async function markAllNotificationsRead() {
    await fetchWithAuth('/api/notifications/mark-all-read', { method: 'PUT', headers: authHeaders() });
    refreshNotifications(); // re-render without the unread styling and badge
}

// Close the panel if the user clicks anywhere outside of it
document.addEventListener('click', (e) => {
    if (notifPanelOpen && !document.getElementById('notifPanel').contains(e.target)
        && !document.getElementById('notifBellBtn').contains(e.target)) {
        document.getElementById('notifPanel').style.display = 'none';
        notifPanelOpen = false;
    }
});

// --- Real-time Search Filter ---
let searchDebounceTimer = null;
function filterTickets() {
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
        const q = document.getElementById('ticketSearch').value.trim();
        loadConsolePage(0, q);
    }, 400);
}

async function loadConsolePage(page, q) {
    consoleCurrentPage = page;
    consoleSearchQuery = q || '';

    const params = new URLSearchParams({ page, size: 20 });
    if (consoleSearchQuery) params.set('q', consoleSearchQuery);

    try {
        const response = await fetchWithAuth(`/api/tickets/search?${params}`, {
            headers: authHeaders()
        });
        if (!response.ok) return;
        const data = await response.json();
        renderTable(data.content || []);
        renderPaginationControls(data);
    } catch (e) {
        console.error('Console load error:', e);
    }
}

function renderPaginationControls(pageData) {
    const info    = document.getElementById('paginationInfo');
    const prevBtn = document.getElementById('prevPageBtn');
    const nextBtn = document.getElementById('nextPageBtn');
    if (!info || !prevBtn || !nextBtn) return;

    const { number, totalPages, totalElements, numberOfElements } = pageData;
    const start = number * 20 + 1;
    const end   = number * 20 + numberOfElements;

    info.innerText = totalElements > 0
        ? `Showing ${start}–${end} of ${totalElements} tickets`
        : 'No tickets found';

    prevBtn.disabled = number === 0;
    nextBtn.disabled = number >= totalPages - 1;
}

function changePage(delta) {
    loadConsolePage(consoleCurrentPage + delta, consoleSearchQuery);
}

function handleTicketCheckbox(checkbox) {
    const id = parseInt(checkbox.dataset.id);
    if (checkbox.checked) {
        selectedTicketIds.add(id);
    } else {
        selectedTicketIds.delete(id);
    }
    updateBulkToolbar();
}

function toggleSelectAll(masterCheckbox) {
    document.querySelectorAll('.ticket-checkbox').forEach(cb => {
        cb.checked = masterCheckbox.checked;
        const id = parseInt(cb.dataset.id);
        if (masterCheckbox.checked) {
            selectedTicketIds.add(id);
        } else {
            selectedTicketIds.delete(id);
        }
    });
    updateBulkToolbar();
}

function updateBulkToolbar() {
    const toolbar   = document.getElementById('bulkToolbar');
    const countSpan = document.getElementById('bulkCount');
    if (!toolbar || !countSpan) return;

    if (selectedTicketIds.size > 0) {
        toolbar.style.display = 'flex';
        countSpan.innerText = selectedTicketIds.size + ' selected';

        const role        = localStorage.getItem('jwt_role');
        const agentSelect = document.getElementById('bulkAgentSelect');
        const agentWrapper = document.getElementById('bulkAgentWrapper');
        // Hide admin-only status options for developers
        const isAdmin = role === 'ADMIN';
        document.querySelectorAll('#bulkStatusSelect .admin-only').forEach(opt => {
            opt.style.display = isAdmin ? 'block' : 'none';
        });

        if (role === 'ADMIN') {
            if (agentWrapper) agentWrapper.style.display = 'block';
            agentSelect.innerHTML = '<option value="">Assign agent...</option>';
            allUsers.forEach(u => {
                const opt = document.createElement('option');
                opt.value = u.username;
                opt.textContent = u.username + ' (' + u.role + ')';
                agentSelect.appendChild(opt);
            });
        } else {
            if (agentWrapper) agentWrapper.style.display = 'none';
        }
    } else {
        toolbar.style.display = 'none';
    }
}

function clearBulkSelection() {
    selectedTicketIds.clear();
    document.querySelectorAll('.ticket-checkbox').forEach(cb => cb.checked = false);
    const master = document.getElementById('selectAllCheckbox');
    if (master) master.checked = false;
    updateBulkToolbar();
}

async function applyBulkAction() {
    const status      = document.getElementById('bulkStatusSelect').value;
    const agent       = document.getElementById('bulkAgentSelect').value;

    if (!status && !agent) {
        alert('Select a status or agent to apply.');
        return;
    }

    const role = localStorage.getItem('jwt_role');
    const body = { ticketIds: Array.from(selectedTicketIds) };
    if (status) body.status = status;
    if (agent && role === 'ADMIN') body.assignedAgent = agent;

    try {
        const res = await fetchWithAuth('/api/tickets/bulk-update', {
            method: 'PUT',
            headers: authHeaders(),
            body: JSON.stringify(body)
        });
        const result = await res.json();
        clearBulkSelection();
        document.getElementById('bulkStatusSelect').value = '';
        document.getElementById('bulkAgentSelect').value  = '';
        refreshDataPipeline();
        loadConsolePage(consoleCurrentPage, consoleSearchQuery);
        alert(`${result.updated} ticket(s) updated successfully.`);
    } catch (e) {
        console.error('Bulk update failed:', e);
    }
}

// --- 4. Admin Feature ---
async function adminRegisterUser() {
    const u = document.getElementById('regUser').value;
    const p = document.getElementById('regPass').value;
    const r = document.getElementById('regRole').value;
    const msgBox = document.getElementById('adminMsg');

    try {
        const response = await fetchWithAuth('/api/auth/register', {
            method: 'POST', headers: authHeaders(),
            body: JSON.stringify({ username: u, password: p, role: r })
        });
        if (response.ok) {
            msgBox.style.display = 'block'; msgBox.style.backgroundColor = '#E6F4EA'; msgBox.style.color = '#1E8E3E';
            msgBox.innerText = `Success: ${u} provisioned as ${r}`;
            document.getElementById('regUser').value = ''; document.getElementById('regPass').value = '';
        } else {
            msgBox.style.display = 'block'; msgBox.style.backgroundColor = '#FCE8E6'; msgBox.style.color = '#C5221F';
            msgBox.innerText = "Error: Could not register user (Might already exist).";
        }
    } catch(e) {
        msgBox.style.display = 'block'; msgBox.style.backgroundColor = '#FCE8E6'; msgBox.style.color = '#C5221F';
        msgBox.innerText = "Network Error.";
    }
}

// --- Forms & Modals ---
document.getElementById('ticketForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    await fetchWithAuth('/api/tickets', {
        method: 'POST', headers: authHeaders(),
        body: JSON.stringify({
            title: document.getElementById('title').value,
            description: document.getElementById('description').value,
            priority: document.getElementById('priority').value,
            category: { id: Number(document.getElementById('category').value) },
            status: 'NEW'
        })
    });
    document.getElementById('ticketForm').reset();
    document.getElementById('createModal').close();

    // Switch to Ops view to see the new ticket
    switchView('viewConsole', document.querySelectorAll('.nav-item')[2]);
    refreshDataPipeline();
    loadConsolePage(0, '');
});

async function startProgress(id) {
    const ticket = currentTickets.find(t => t.id === id);
    if (!ticket) return;
    await fetchWithAuth(`/api/tickets/${id}`, {
        method: 'PUT', headers: authHeaders(),
        // Preserve the original description — never overwrite incident details
        body: JSON.stringify({ title: ticket.title, description: ticket.description, priority: ticket.priority, status: 'IN_PROGRESS' })
    });
    refreshDataPipeline();
    loadConsolePage(consoleCurrentPage, consoleSearchQuery);
}

function prepResolve(id) {
    const ticket = currentTickets.find(t => t.id === id);
    if (!ticket) return;
    ticketIdPendingResolve = id;
    document.getElementById('resTitle').innerText = ticket.title;
    document.getElementById('resPriority').innerText = ticket.priority;
    document.getElementById('resolveModal').showModal();
}

async function submitResolve() {
    if (!ticketIdPendingResolve) return;
    const ticket = currentTickets.find(t => t.id === ticketIdPendingResolve);
    if (!ticket) return;

    const notes = document.getElementById('resolveNotes').value.trim();
    // Append resolution notes to original description if provided — never replace it
    const finalDescription = notes
        ? ticket.description + '\n\n--- Resolution Notes ---\n' + notes
        : ticket.description;

    await fetchWithAuth(`/api/tickets/${ticketIdPendingResolve}`, {
        method: 'PUT', headers: authHeaders(),
        body: JSON.stringify({
            title: ticket.title,
            description: finalDescription,
            priority: ticket.priority,
            status: 'RESOLVED'
        })
    });
    document.getElementById('resolveModal').close();
    document.getElementById('resolveNotes').value = '';
    ticketIdPendingResolve = null;
    refreshDataPipeline();
    loadConsolePage(consoleCurrentPage, consoleSearchQuery);
}

async function suggestClassification() {
    const title = document.getElementById('title')?.value?.trim();
    const description = document.getElementById('description')?.value?.trim();

    if (!title) {
        alert('Please enter a title first so the classifier has something to work with.');
        return;
    }

    const btn = document.getElementById('suggestBtn');
    btn.textContent = '⏳ Analysing...';
    btn.disabled = true;

    try {
        const response = await fetch('/api/tickets/suggest', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ title, description })
        });

        if (!response.ok) throw new Error('Suggestion request failed');

        const result = await response.json();

        if (result.suggestedCategoryId) {
            const categorySelect = document.getElementById('category');
            if (categorySelect) {
                categorySelect.value = result.suggestedCategoryId;
                document.getElementById('categoryAiChip').style.display = 'inline-flex';
            }
        }

        if (result.suggestedPriority) {
            const prioritySelect = document.getElementById('priority');
            if (prioritySelect) {
                prioritySelect.value = result.suggestedPriority;
                document.getElementById('priorityAiChip').style.display = 'inline-flex';
            }
        }

        const pct = result.confidence;
        btn.textContent = pct > 0
            ? `✨ Suggested (${pct}% confidence) — override anytime`
            : '✨ No clear match found — please select manually';

    } catch (err) {
        btn.textContent = '✨ Suggest Category & Priority';
        console.error('Classification error:', err);
    } finally {
        btn.disabled = false;
    }
}

function switchDetailTab(tab) {
    const commentsPanel = document.getElementById('detailPanelComments');
    const historyPanel  = document.getElementById('detailPanelHistory');
    const tabComments   = document.getElementById('tabComments');
    const tabHistory    = document.getElementById('tabHistory');

    const activeStyle   = 'var(--g-blue)';
    const inactiveStyle = 'var(--g-text-muted)';

    if (tab === 'comments') {
        commentsPanel.style.display = 'block';
        historyPanel.style.display  = 'none';
        tabComments.style.borderBottomColor = activeStyle;
        tabComments.style.color             = activeStyle;
        tabHistory.style.borderBottomColor  = 'transparent';
        tabHistory.style.color              = inactiveStyle;
    } else {
        commentsPanel.style.display = 'none';
        historyPanel.style.display  = 'block';
        tabComments.style.borderBottomColor = 'transparent';
        tabComments.style.color             = inactiveStyle;
        tabHistory.style.borderBottomColor  = activeStyle;
        tabHistory.style.color              = activeStyle;
        loadTicketHistory(currentDetailTicketId); // fetch on first click
    }
}

async function loadTicketHistory(ticketId) {
    const historyList = document.getElementById('historyList');
    historyList.innerHTML = '<p style="color: var(--g-text-muted); font-size: 13px;">Loading...</p>';

    try {
        const token = localStorage.getItem('jwt_token');
        const res = await fetch(`/api/tickets/${ticketId}/history`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        const entries = await res.json();

        if (!entries.length) {
            historyList.innerHTML = '<p style="color: var(--g-text-muted); font-size: 13px;">No changes recorded yet.</p>';
            return;
        }

        historyList.innerHTML = entries.map(e => `
            <div style="display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--g-border);">
                <div style="width: 32px; height: 32px; border-radius: 50%; background: var(--g-blue-light);
                            color: var(--g-blue); display: flex; align-items: center; justify-content: center;
                            font-size: 13px; font-weight: 600; flex-shrink: 0;">
                    ${(e.changedBy || '?')[0].toUpperCase()}
                </div>
                <div style="flex: 1;">
                    <div style="font-size: 13px; font-weight: 500; margin-bottom: 2px;">
                        <span style="color: var(--g-blue);">${e.changedBy || 'System'}</span>
                        changed <strong>${formatFieldName(e.fieldName)}</strong>
                    </div>
                    <div style="font-size: 12px; color: var(--g-text-muted); margin-bottom: 4px;">
                        ${formatHistoryTimestamp(e.changedAt)}
                    </div>
                    <div style="font-size: 12px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap;">
                        <span style="background: #FCE8E6; color: var(--g-red); padding: 2px 8px; border-radius: 10px;">
                            ${e.oldValue || '—'}
                        </span>
                        <span style="color: var(--g-text-muted);">→</span>
                        <span style="background: #E6F4EA; color: var(--g-green); padding: 2px 8px; border-radius: 10px;">
                            ${e.newValue || '—'}
                        </span>
                    </div>
                </div>
            </div>
        `).join('');
    } catch (err) {
        historyList.innerHTML = '<p style="color: var(--g-red); font-size: 13px;">Failed to load history.</p>';
    }
}

function formatFieldName(field) {
    const map = {
        status: 'Status', priority: 'Priority', category: 'Category',
        assignedTo: 'Assigned To', title: 'Title', description: 'Description'
    };
    return map[field] || field;
}

function formatHistoryTimestamp(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    return d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}