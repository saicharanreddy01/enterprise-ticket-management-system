let currentUserRoles = [];
let allTicketsCache = [];
let currentFilterState = 'ALL';

// ── JWT Helper ───────────────────────────────────────────────
// Single function that adds the Authorization header to every fetch call
function authHeaders(extra = {}) {
    const token = localStorage.getItem('jwt_token');
    return {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        ...extra
    };
}

// If no token in localStorage, redirect to login immediately
function guardAuth() {
    if (!localStorage.getItem('jwt_token')) {
        window.location.href = '/login.html';
    }
}

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
    } else if (targetView === 'ADMIN') {
        document.getElementById('viewAdmin').classList.add('active-view');
        document.getElementById('linkAdmin').classList.add('active');
        loadUsersTable();
    }
}

// 3. User Credentials Context Query Verification
async function checkUserProfile() {
    try {
        const response = await fetch('/api/users/me', {
            headers: authHeaders()
        });
        if (response.status === 401) {
            // Token expired or invalid — clear and redirect to login
            localStorage.clear();
            window.location.href = '/login.html';
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
            document.getElementById('linkAdmin').style.display = 'flex';
        } else {
            document.getElementById('currentUserRole').className = "badge bg-primary-subtle text-primary border border-primary-subtle ms-2 small";
            document.getElementById('linkAdmin').style.display = 'none';
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
        const response = await fetch('/api/tickets', {
            headers: authHeaders()
        });
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
                <td><span class="text-dark">${formattedTime}</span>${auditorLogs}</td>
                <td><span class="badge ${statusBadge} badge-pill-flat">${ticket.status}</span></td>
                <td class="pe-4">${operationsAction}</td>
            </tr>`;
    });
}

// 8. Dom Lifecycle Scope Initialization
document.addEventListener("DOMContentLoaded", () => {
    // Guard — redirect to login if no token
    guardAuth();

    const ticketForm = document.getElementById('ticketForm');
    const logoutBtn = document.getElementById('logoutBtn');

    if (ticketForm) {
        ticketForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const title = document.getElementById('title').value;
            const description = document.getElementById('description').value;
            const priority = document.getElementById('priority').value;
            try {
                const response = await fetch('/api/tickets', {
                    method: 'POST',
                    headers: authHeaders(),
                    body: JSON.stringify({ title, description, status: 'OPEN', priority })
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
            // Clear JWT token and redirect to login
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('jwt_username');
            localStorage.removeItem('jwt_role');
            window.location.href = '/login.html';
        });
    }

    switchView('DASHBOARD');
    checkUserProfile();
});

// 9. Toggle password visibility
function togglePasswordVisibility() {
    const input = document.getElementById('newPassword');
    const eyeIcon = document.getElementById('eyeIcon');
    const isHidden = input.type === 'password';
    input.type = isHidden ? 'text' : 'password';
    eyeIcon.innerHTML = isHidden
        ? `<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line>`
        : `<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>`;
}

// 10. Load all users into the admin users table
async function loadUsersTable() {
    try {
        const response = await fetch('/api/auth/users', {
            headers: authHeaders()
        });
        const users = await response.json();
        const tbody = document.getElementById('usersTableBody');
        tbody.innerHTML = '';
        if (users.length === 0) {
            tbody.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-4">No users found.</td></tr>`;
            return;
        }
        users.forEach(user => {
            const roleBadge = user.role === 'ADMIN'
                ? 'bg-danger-subtle text-danger border border-danger-subtle'
                : 'bg-primary-subtle text-primary border border-primary-subtle';
            tbody.innerHTML += `
                <tr style="border-bottom: 1px solid #F1F3F4;">
                    <td class="ps-4 text-muted"><strong>#${user.id}</strong></td>
                    <td class="fw-semibold">${user.username}</td>
                    <td><span class="badge ${roleBadge} badge-pill-flat">${user.role}</span></td>
                    <td class="pe-4">
                        <button class="btn btn-sm btn-outline-danger py-0 px-2" style="font-size:11px;"
                            onclick="deleteUser(${user.id}, '${user.username}')">Delete</button>
                    </td>
                </tr>`;
        });
    } catch (error) {
        console.error('Error loading users:', error);
    }
}

// 11. Create a new user from the admin panel form
async function createUser() {
    const username = document.getElementById('newUsername').value.trim();
    const password = document.getElementById('newPassword').value.trim();
    const role = document.getElementById('newRole').value;
    const msgEl = document.getElementById('createUserMsg');
    if (!username || !password) {
        msgEl.innerHTML = `<span class="text-danger">Username and password are required.</span>`;
        return;
    }
    try {
        const response = await fetch('/api/auth/register', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ username, password, role })
        });
        const text = await response.text();
        if (response.ok) {
            msgEl.innerHTML = `<span class="text-success">✓ ${text}</span>`;
            document.getElementById('newUsername').value = '';
            document.getElementById('newPassword').value = '';
            loadUsersTable();
        } else {
            msgEl.innerHTML = `<span class="text-danger">✗ ${text}</span>`;
        }
    } catch (error) {
        msgEl.innerHTML = `<span class="text-danger">Network error. Try again.</span>`;
    }
}

// 12. Delete a user from the admin panel
async function deleteUser(id, username) {
    if (!confirm(`Are you sure you want to delete user "${username}"?`)) return;
    try {
        const response = await fetch(`/api/auth/users/${id}`, {
            method: 'DELETE',
            headers: authHeaders()
        });
        const text = await response.text();
        if (response.ok) {
            loadUsersTable();
        } else {
            alert(`Error: ${text}`);
        }
    } catch (error) {
        console.error('Error deleting user:', error);
    }
}