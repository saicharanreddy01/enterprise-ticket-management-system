// --- 1. Auth & Security ---
function authHeaders() { return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('jwt_token')}` }; }

function guardAuth() {
    if (!localStorage.getItem('jwt_token')) window.location.href = '/landing.html';
}

async function fetchWithAuth(url, options = {}) {
    let response = await fetch(url, options);
    if (response.status === 401) {
        const refreshToken = localStorage.getItem('jwt_refresh_token');
        if (refreshToken) {
            const refreshResponse = await fetch('/api/auth/refresh', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken })
            });
            if (refreshResponse.ok) {
                const data = await refreshResponse.json();
                localStorage.setItem('jwt_token', data.token);
                if (data.refreshToken) localStorage.setItem('jwt_refresh_token', data.refreshToken);
                if (options.headers) options.headers['Authorization'] = `Bearer ${data.token}`;
                return await fetch(url, options);
            }
        }
        logout();
    }
    return response;
}

async function logout() {
    const refreshToken = localStorage.getItem('jwt_refresh_token');
    if (refreshToken) {
        try {
            // Revoke the token server-side so it can't be used even if it was stolen
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken })
            });
        } catch (e) {
            // Network failure shouldn't trap the user on the page — fall through and clear locally anyway
            console.error('Logout revocation failed:', e);
        }
    }
    localStorage.clear();
    window.location.href = '/landing.html';
}

// --- 2. State & Init ---
let ticketIdPendingResolve = null;
let currentTickets = [];

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

    // Live Clock
    setInterval(() => {
        const d = new Date();
        document.getElementById('liveClock').innerText = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }, 1000);

    refreshDataPipeline();
    loadCategories();
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

            renderTable(tickets);
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
            <td style="color: var(--g-blue); font-weight: 500;">#${t.id}</td>
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

// --- Real-time Search Filter ---
function filterTickets() {
    const input = document.getElementById("ticketSearch").value.toLowerCase();
    const rows = document.querySelectorAll(".ticket-row");

    rows.forEach(row => {
        // Search across the entire row's text content
        const rowText = row.innerText.toLowerCase();
        row.style.display = rowText.includes(input) ? "" : "none";
    });
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
}