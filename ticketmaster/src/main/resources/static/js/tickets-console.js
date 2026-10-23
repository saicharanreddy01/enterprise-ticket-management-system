/**
 * tickets-console.js — Operations Console (ticket list/table)
 * =============================================================
 * Depends on: core.js
 *
 * Everything behind the main ticket table: search/filter/sort, pagination,
 * quick filters ("Assigned to me" / "My requests"), bulk selection + bulk
 * actions, and the reference data loaders (categories, users, agent workload)
 * that feed the create-ticket form's dropdowns and the workload widget.
 */

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
            // Also populate the filter bar category dropdown
            const filterCat = document.getElementById('filterCategory');
            if (filterCat) {
                categories.forEach(c => {
                    const opt = document.createElement('option');
                    opt.value = c.id;
                    opt.textContent = c.name;
                    filterCat.appendChild(opt);
                });
            }
            const bulkCat = document.getElementById('bulkCategorySelect');
            if (bulkCat) {
                categories.forEach(c => {
                    const opt = document.createElement('option');
                    opt.value = c.id;
                    opt.textContent = c.name;
                    bulkCat.appendChild(opt);
                });
            }
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

async function loadAgentWorkload() {
    try {
        const res = await fetchWithAuth('/api/users/workload', { headers: authHeaders() });
        if (res.ok) agentWorkload = await res.json();
    } catch (e) {
        console.error('Failed to load agent workload:', e);
    }
}

// --- 3. Pipeline & Table Rendering ---
// Previously this fetched EVERY ticket in the system (with comments eagerly
// joined, no pagination) just to count statuses and percentages in JS. That
// doesn't scale — at real volume it means shipping a multi-hundred-MB
// response to the browser to render four numbers and a donut chart. Now it
// calls one aggregation endpoint that does the counting in SQL and returns a
// handful of numbers regardless of ticket volume.
async function refreshDataPipeline() {
    try {
        const response = await fetchWithAuth('/api/reports/dashboard-summary', { headers: authHeaders() });
        if (response.ok) {
            const summary = await response.json();

            document.getElementById('metricTotal').innerText    = summary.total;
            document.getElementById('metricOpen').innerText     = summary.openCount;
            document.getElementById('metricResolved').innerText = summary.resolvedCount;

            renderDashboardCharts(summary);
        }
    } catch (e) { console.error("Pipeline Sync Error:", e); }

    // Independent of the aggregate summary above — its own small, scoped call.
    renderDevPersonalWidget();
}

// Shared with ticket-detail.js — one urgency-badge language across the whole app.
function buildSlaChipHtml(t) {
    if (t.slaBreached) {
        return `<span class="sla-chip sla-breach"><span class="sla-dot"></span>Breached</span>`;
    }
    if (t.slaDueDate) {
        const due = new Date(t.slaDueDate);
        const hoursLeft = (due.getTime() - Date.now()) / 3600000;
        const dueLabel = due.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
        const urgency = hoursLeft <= 4 ? 'sla-warn' : 'sla-ok';
        return `<span class="sla-chip ${urgency}"><span class="sla-dot"></span>${dueLabel}</span>`;
    }
    return `<span class="sla-chip sla-none"><span class="sla-dot"></span>No SLA set</span>`;
}

function renderTable(tickets) {
    const tbody = document.getElementById('ticketTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (tickets.length === 0) {
        tbody.innerHTML = `
        <tr>
            <td colspan="8" style="padding: 64px 24px; text-align:center;">
                <span class="material-symbols-rounded"
                      style="font-size:48px; color:var(--g-text-muted); display:block; margin-bottom:12px;">
                    inbox
                </span>
                <p style="font-size:15px; font-weight:500; color:var(--g-text-main); margin:0 0 6px 0;">
                    No tickets found
                </p>
                <p style="font-size:13px; color:var(--g-text-muted); margin:0;">
                    Try adjusting your search or filters
                </p>
            </td>
        </tr>`;
        return;
    }

    tickets.forEach(t => {
        let statusClass = 'ticket-chip';
        if (['NEW', 'OPEN'].includes(t.status)) statusClass += ' chip-new';
        else if (t.status === 'IN_PROGRESS') statusClass += ' chip-progress';
        else if (t.status === 'RESOLVED') statusClass += ' chip-resolved';

        let categoryName = t.category && t.category.name ? escapeHtml(t.category.name) : 'Uncategorized';

        // --- Priority: a real badge, not just colored text — the eye should
        // land on HIGH-priority rows before it reads a single word. ---
        const priorityClass = t.priority === 'HIGH' ? 'priority-high' : t.priority === 'MEDIUM' ? 'priority-medium' : 'priority-low';
        const priorityHtml = `<span class="priority-chip ${priorityClass}">${t.priority || 'LOW'}</span>`;

        // --- SLA: color-coded urgency instead of a bare date, so "at risk"
        // is visible at a glance across a whole page of rows. ---
        const slaHtml = buildSlaChipHtml(t);

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
            <td>${priorityHtml}</td>
            <td><span class="${statusClass}">${t.status}</span></td>
            <td>${slaHtml}</td>
            <td style="text-align: right;">${actionBtn}</td>
        `;
        tbody.appendChild(tr);
    });
}

// --- Real-time Search Filter ---
let searchDebounceTimer = null;
function filterTickets() {
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
        applyFilters();
    }, 400);
}

async function loadConsolePage(page, q, status, priority, categoryId) {
    consoleCurrentPage = page;
    consoleSearchQuery = q || '';

    renderSkeletonRows(
        document.getElementById('ticketTableBody'),
        9, 8,
        ['20%', '30%', '80%', '60%', '60%', '70%', '65%', '30%', '40%']
    );

    const currentUser = localStorage.getItem('jwt_username');
    const params = new URLSearchParams({ page, size: 20, sort: consoleSortParam });
    if (consoleSearchQuery) params.set('q', consoleSearchQuery);
    if (status)     params.set('status', status);
    if (priority)   params.set('priority', priority);
    if (categoryId) params.set('categoryId', categoryId);

    // Quick filters and role-locked filters
    if (consoleLockedRaisedBy) {
        params.set('raisedBy', consoleLockedRaisedBy);
    } else if (quickFilterAssignedToMe && currentUser) {
        params.set('assignedAgent', currentUser);
    } else if (quickFilterRaisedByMe && currentUser) {
        params.set('raisedBy', currentUser);
    }

    try {
        const response = await fetchWithAuth(`/api/tickets/search?${params}`, {
            headers: authHeaders()
        });
        if (!response.ok) return;
        const data = await response.json();
        // Backs the console-row action fallback in ticket-detail.js
        // (startProgress/prepResolve when the detail modal isn't open) —
        // these only ever need "whatever's visible on screen right now,"
        // not a global list, so the current page's own content is enough.
        currentTickets = data.content || [];
        renderTable(currentTickets);
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
function applyFilters() {
    const status     = document.getElementById('filterStatus')?.value || '';
    const priority   = document.getElementById('filterPriority')?.value || '';
    const categoryId = document.getElementById('filterCategory')?.value || '';
    const q          = document.getElementById('ticketSearch')?.value?.trim() || '';

    if (q) rememberRecentSearch(q);
    hideSearchSuggestions();

    const activeCount = [status, priority, categoryId].filter(v => v !== '').length;
    const countBadge  = document.getElementById('activeFilterCount');
    const clearBtn    = document.getElementById('clearFiltersBtn');

    if (countBadge) {
        countBadge.style.display = activeCount > 0 ? 'inline-block' : 'none';
        countBadge.innerText     = activeCount + ' filter' + (activeCount > 1 ? 's' : '') + ' active';
    }
    if (clearBtn) clearBtn.style.display = activeCount > 0 ? 'inline-block' : 'none';

    quickFilterAssignedToMe = false;
    quickFilterRaisedByMe   = false;
    const assignedBtn = document.getElementById('filterAssignedToMe');
    const raisedBtn   = document.getElementById('filterRaisedByMe');
    if (assignedBtn) { assignedBtn.style.background = 'transparent'; assignedBtn.style.color = 'var(--g-text-muted)'; assignedBtn.style.borderColor = 'var(--g-border)'; }
    if (raisedBtn)   { raisedBtn.style.background   = 'transparent'; raisedBtn.style.color   = 'var(--g-text-muted)'; raisedBtn.style.borderColor   = 'var(--g-border)'; }
    loadConsolePage(0, q, status, priority, categoryId ? Number(categoryId) : null);
}

function clearFilters() {
    document.getElementById('filterStatus').value   = '';
    document.getElementById('filterPriority').value = '';
    document.getElementById('filterCategory').value = '';
    document.getElementById('activeFilterCount').style.display = 'none';
    document.getElementById('clearFiltersBtn').style.display   = 'none';
    quickFilterAssignedToMe = false;
    quickFilterRaisedByMe   = false;
    document.getElementById('subFilterAssigned')?.classList.remove('active');
    document.getElementById('subFilterRaised')?.classList.remove('active');
    loadConsolePage(0, consoleSearchQuery);
}

function applySort() {
    consoleSortParam = document.getElementById('consoleSortSelect')?.value || 'id,desc';
    loadConsolePage(0, consoleSearchQuery);
}

function setMyRequestsMode() {
    const currentUser = localStorage.getItem('jwt_username');
    consoleLockedRaisedBy = currentUser;
    quickFilterAssignedToMe = false;
    quickFilterRaisedByMe   = false;
    loadConsolePage(0, '');
}

function toggleQuickFilter(filterName) {
    if (consoleLockedRaisedBy) return; // USER role — locked, ignore

    if (filterName === 'assignedToMe') {
        quickFilterAssignedToMe = !quickFilterAssignedToMe;
        quickFilterRaisedByMe   = false; // mutually exclusive
    } else if (filterName === 'raisedByMe') {
        quickFilterRaisedByMe   = !quickFilterRaisedByMe;
        quickFilterAssignedToMe = false;
    }

    // Update pill button styles
    const assignedBtn = document.getElementById('filterAssignedToMe');
    const raisedBtn   = document.getElementById('filterRaisedByMe');
    if (assignedBtn) {
        assignedBtn.style.background    = quickFilterAssignedToMe ? 'var(--g-blue)' : 'transparent';
        assignedBtn.style.color         = quickFilterAssignedToMe ? '#fff' : 'var(--g-text-muted)';
        assignedBtn.style.borderColor   = quickFilterAssignedToMe ? 'var(--g-blue)' : 'var(--g-border)';
    }
    if (raisedBtn) {
        raisedBtn.style.background  = quickFilterRaisedByMe ? 'var(--g-blue)' : 'transparent';
        raisedBtn.style.color       = quickFilterRaisedByMe ? '#fff' : 'var(--g-text-muted)';
        raisedBtn.style.borderColor = quickFilterRaisedByMe ? 'var(--g-blue)' : 'var(--g-border)';
    }

    loadConsolePage(0, consoleSearchQuery);
}

function applyNavQuickFilter(filterType) {
    if (consoleLockedRaisedBy) return; // USER role — locked

    quickFilterAssignedToMe = filterType === 'assignedToMe';
    quickFilterRaisedByMe   = filterType === 'raisedByMe';

    // Update sub-item highlight
    const assignedItem = document.getElementById('subFilterAssigned');
    const raisedItem   = document.getElementById('subFilterRaised');
    if (assignedItem) assignedItem.classList.toggle('active', quickFilterAssignedToMe);
    if (raisedItem)   raisedItem.classList.toggle('active',   quickFilterRaisedByMe);

    // Make sure we're on the console view
    const consoleParent = document.getElementById('consoleNavParent');
    if (!document.getElementById('viewConsole')?.classList.contains('active-view')) {
        switchView('viewConsole', consoleParent);
    } else {
        loadConsolePage(0, consoleSearchQuery);
    }
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
                const count = agentWorkload[u.username] || 0;
                opt.textContent = `${u.username} (${u.role}) — ${count} open`;
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
    const status   = document.getElementById('bulkStatusSelect').value;
    const agent    = document.getElementById('bulkAgentSelect').value;
    const priority = document.getElementById('bulkPrioritySelect').value;
    const category = document.getElementById('bulkCategorySelect').value;

    if (!status && !agent && !priority && !category) {
        showToast('Select at least one field to update.', 'warning');
        return;
    }

    const role = localStorage.getItem('jwt_role');
    const body = { ticketIds: Array.from(selectedTicketIds) };
    if (status)   body.status   = status;
    if (priority) body.priority = priority;
    if (category) body.categoryId = Number(category);
    if (agent && role === 'ADMIN') body.assignedAgent = agent;

    try {
        const res = await fetchWithAuth('/api/tickets/bulk-update', {
            method: 'PUT',
            headers: authHeaders(),
            body: JSON.stringify(body)
        });
        const result = await res.json();
        clearBulkSelection();
        document.getElementById('bulkStatusSelect').value   = '';
        document.getElementById('bulkAgentSelect').value    = '';
        document.getElementById('bulkPrioritySelect').value = '';
        document.getElementById('bulkCategorySelect').value = '';
        refreshDataPipeline();
        loadConsolePage(consoleCurrentPage, consoleSearchQuery);
        showToast(`${result.updated} ticket(s) updated successfully.`, 'success');
    } catch (e) {
        console.error('Bulk update failed:', e);
    }
}

// =========================================================================
// SEARCH SUGGESTIONS — the search bar remembers your last few searches
// instead of being a dead-end box you have to type into from scratch
// every time. Purely client-side (localStorage), no backend involved.
// =========================================================================
const RECENT_SEARCH_KEY = 'tm_recent_ticket_searches';
const RECENT_SEARCH_MAX = 6;

function getRecentSearches() {
    try {
        return JSON.parse(localStorage.getItem(RECENT_SEARCH_KEY) || '[]');
    } catch (e) { return []; }
}

function rememberRecentSearch(term) {
    if (!term) return;
    let list = getRecentSearches().filter(t => t.toLowerCase() !== term.toLowerCase());
    list.unshift(term);
    list = list.slice(0, RECENT_SEARCH_MAX);
    localStorage.setItem(RECENT_SEARCH_KEY, JSON.stringify(list));
}

function clearRecentSearches(e) {
    e.stopPropagation();
    localStorage.removeItem(RECENT_SEARCH_KEY);
    renderSearchSuggestions();
}

function runRecentSearch(term) {
    const input = document.getElementById('ticketSearch');
    input.value = term;
    hideSearchSuggestions();
    applyFilters();
}

function showSearchSuggestions() {
    renderSearchSuggestions();
    document.getElementById('searchSuggestPanel')?.classList.add('open');
    document.getElementById('ticketSearch')?.setAttribute('aria-expanded', 'true');
}

function hideSearchSuggestions() {
    document.getElementById('searchSuggestPanel')?.classList.remove('open');
    document.getElementById('ticketSearch')?.setAttribute('aria-expanded', 'false');
}

function renderSearchSuggestions() {
    const panel = document.getElementById('searchSuggestPanel');
    if (!panel) return;
    const recent = getRecentSearches();

    if (recent.length === 0) {
        panel.innerHTML = `<div class="search-suggest-empty">Your recent searches will show up here.</div>`;
        return;
    }

    panel.innerHTML = `
        <div class="search-suggest-label">Recent searches</div>
        ${recent.map(term => `
            <div class="search-suggest-item" onmousedown="event.preventDefault(); runRecentSearch('${escapeHtml(term).replace(/'/g, "\\'")}')">
                <span class="material-symbols-rounded" aria-hidden="true">history</span>
                <span>${escapeHtml(term)}</span>
            </div>
        `).join('')}
        <button type="button" class="search-suggest-clear" onmousedown="event.preventDefault();" onclick="clearRecentSearches(event)">Clear recent searches</button>
    `;
}

// Close the panel on outside click, and on Escape while the search input is focused.
document.addEventListener('click', (e) => {
    const searchBar = document.getElementById('ticketSearch')?.closest('.search-bar');
    if (searchBar && !searchBar.contains(e.target)) hideSearchSuggestions();
});
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && document.activeElement?.id === 'ticketSearch') {
        hideSearchSuggestions();
        document.activeElement.blur();
    }
});