let currentUserRoles = [];
let allTicketsCache = [];
let currentFilterState = 'ALL';

// 1. Live Operational Clock Tracker
function updateClock() {
    const now = new Date();
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    const formattedDate = `${days[now.getDay()]} , ${now.getDate()} ${months[now.getMonth()]} ${now.getFullYear()} , ${now.toTimeString().split(' ')[0]}`;
    const clockElement = document.getElementById('liveClock');
    if (clockElement) clockElement.innerText = formattedDate;
}
setInterval(updateClock, 1000);

// 2. Client-Side SPA Tab Switcher
function switchView(targetView) {
    document.querySelectorAll('.app-view').forEach(view => view.classList.remove('active-view'));
    document.querySelectorAll('.nav-link-custom').forEach(link => link.classList.remove('active'));

    if (targetView === 'DASHBOARD') {
        document.getElementById('viewDashboard').classList.add('active-view');
        document.getElementById('linkDashboard').classList.add('active');
    } else if (targetView === 'CREATE') {
        document.getElementById('viewCreate').classList.add('active-view');
        document.getElementById('linkCreate').classList.add('active');
    } else if (targetView === 'CONSOLE') {
        document.getElementById('viewConsole').classList.add('active-view');
        document.getElementById('linkConsole').classList.add('active');
    }
}

// 3. User Credentials Context Query Verification
async function checkUserProfile() {
    try {
        const response = await fetch('/api/users/me');
        if (response.status === 401) {
            window.location.reload();
            return;
        }
        const data = await response.json();
        currentUserRoles = data.roles || [];

        document.getElementById('currentUsername').innerText = data.username;
        document.getElementById('avatarLetter').innerText = data.username.charAt(0).toUpperCase();

        const roleLabel = currentUserRoles.join(', ').replace(/ROLE_/g, '');
        document.getElementById('currentUserRole').innerText = roleLabel;

        if (currentUserRoles.includes('ROLE_ADMIN')) {
            document.getElementById('currentUserRole').className = "badge bg-danger-subtle text-danger border border-danger-subtle ms-2 small";
        } else {
            document.getElementById('currentUserRole').className = "badge bg-primary-subtle text-primary border border-primary-subtle ms-2 small";
        }

        refreshDataPipeline();
        updateClock();
    } catch (error) {
        console.error("Authentication mapping failure:", error);
    }
}

// 4. Synchronization Pipeline
async function refreshDataPipeline() {
    try {
        const response = await fetch('/api/tickets');
        allTicketsCache = await response.json();
        computeMetricsWidget();
        renderTableGrid();
    } catch (error) {
        console.error('Error pipeline sync drop:', error);
    }
}

// 5. In-Memory Statistical Tabulator
function computeMetricsWidget() {
    const total = allTicketsCache.length;
    const open = allTicketsCache.filter(t => t.status === 'OPEN').length;
    const resolved = allTicketsCache.filter(t => t.status === 'RESOLVED').length;

    document.getElementById('metricTotal').innerText = total;
    document.getElementById('metricOpen').innerText = open;
    document.getElementById('metricResolved').innerText = resolved;
}

// 6. Pill Set Segment Switches
function filterGrid(statusType) {
    currentFilterState = statusType;

    const btnAll = document.getElementById('filterAll');
    const btnOpen = document.getElementById('filterOpen');
    const btnResolved = document.getElementById('filterResolved');

    btnAll.className = "fixed-filter-btn btn-light text-secondary bg-transparent";
    btnOpen.className = "fixed-filter-btn btn-light text-secondary bg-transparent";
    btnResolved.className = "fixed-filter-btn btn-light text-secondary bg-transparent";

    if (statusType === 'ALL') btnAll.className = "fixed-filter-btn btn-google-primary rounded text-white";
    if (statusType === 'OPEN') btnOpen.className = "fixed-filter-btn btn-google-primary rounded text-white";
    if (statusType === 'RESOLVED') btnResolved.className = "fixed-filter-btn btn-google-primary rounded text-white";

    renderTableGrid();
}

// 7. Table Row Content Paint Renderer
function renderTableGrid() {
    const tableBody = document.getElementById('ticketTableBody');
    if (!tableBody) return;
    tableBody.innerHTML = '';

    const isAdmin = currentUserRoles.includes('ROLE_ADMIN');

    const filteredTickets = allTicketsCache.filter(ticket => {
        if (currentFilterState === 'ALL') return true;
        return ticket.status === currentFilterState;
    });

    if (filteredTickets.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-5">No tickets found tracking this criteria.</td></tr>`;
        return;
    }

    filteredTickets.forEach(ticket => {
        const isResolved = ticket.status === 'RESOLVED';
        const statusBadge = isResolved ? 'bg-success-subtle text-success border border-success-subtle' : 'bg-warning-subtle text-warning border border-warning-subtle';
        const formattedTime = ticket.createdAt ? new Date(ticket.createdAt).toLocaleString() : 'N/A';

        let priorityBadge = 'bg-secondary-subtle text-secondary border';
        if (ticket.priority === 'HIGH') priorityBadge = 'bg-danger-subtle text-danger border border-danger-subtle';
        if (ticket.priority === 'MEDIUM') priorityBadge = 'bg-info-subtle text-info border border-info-subtle';

        const raisedByStamp = ticket.raisedBy ? ticket.raisedBy : 'System';
        let auditorLogs = `<div class="text-muted mt-1" style="font-size: 11px;">Raised by: <strong>${raisedByStamp}</strong></div>`;
        if (isResolved && ticket.resolvedBy) {
            auditorLogs += `<div class="text-success" style="font-size: 11px;">Resolved by: <strong>${ticket.resolvedBy}</strong></div>`;
        }

        let operationsAction = '-';
        if (isAdmin) {
            operationsAction = isResolved
                ? `<span class="text-muted small">Done ✓</span>`
                : `<button class="btn btn-google-outline py-0 px-2" style="font-size: 11px; height: 24px; color: #4F46E5; border-color: #DADCE0;" onclick="resolveTicket(${ticket.id}, '${ticket.title}', '${ticket.description}', '${ticket.priority}')">Resolve</button>`;
        }

        tableBody.innerHTML += `
            <tr style="border-bottom: 1px solid #F1F3F4;">
                <td class="ps-4 text-muted"><strong>#${ticket.id}</strong></td>
                <td>
                    <span class="badge ${priorityBadge} mb-1" style="font-size: 9px; font-weight: 600;">${ticket.priority}</span><br>
                    <span class="fw-semibold text-dark" style="font-size: 0.95rem;">${ticket.title}</span><br>
                    <span class="text-muted text-xs d-block mt-1" style="max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${ticket.description}</span>
                </td>
                <td>
                    <span class="text-dark">${formattedTime}</span>
                    ${auditorLogs}
                </td>
                <td><span class="badge ${statusBadge} badge-pill-flat">${ticket.status}</span></td>
                <td class="pe-4">${operationsAction}</td>
            </tr>
        `;
    });
}

// 8. Dom Lifecycle Scope Initialization Binding Event Hooks
document.addEventListener("DOMContentLoaded", () => {
    const ticketForm = document.getElementById('ticketForm');
    const logoutBtn = document.getElementById('logoutBtn'); // Variable initialization mapped correctly inside DOM loop scope!

    if (ticketForm) {
        ticketForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const title = document.getElementById('title').value;
            const description = document.getElementById('description').value;
            const priority = document.getElementById('priority').value;

            try {
                const response = await fetch('/api/tickets', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ title, description, status: 'OPEN', priority: priority })
                });

                if (response.ok) {
                    ticketForm.reset();
                    refreshDataPipeline();
                    switchView('CONSOLE');
                } else {
                    alert("Submission rejected: Check validation parameters constraints.");
                }
            } catch (error) {
                console.error('Error executing network save:', error);
            }
        });
    }

    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            // Smoothly bounce user straight to backend session invalidation route mapping
            window.location.href = "/perform_logout";
        });
    }

    switchView('DASHBOARD');
    // Execute core profile evaluation sequence maps
    checkUserProfile();
});