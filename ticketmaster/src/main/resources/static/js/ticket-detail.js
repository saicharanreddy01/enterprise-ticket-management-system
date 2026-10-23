/**
 * ticket-detail.js — Ticket detail modal
 * =============================================================
 * Depends on: core.js, tickets-console.js (refreshDataPipeline)
 *
 * The single biggest feature surface in the app — everything that happens
 * once a ticket is opened: comments, attachments, ticket linking, SLA pause
 * controls, status stepper, history tab, resolve/progress actions, live
 * collision detection ("X is also viewing this ticket"), AI duplicate
 * detection + merge, canned-response insertion, and "Convert to KB article".
 *
 * This file is intentionally the largest of the split — it's one cohesive
 * feature (the ticket detail experience), not a grab-bag. If it keeps growing,
 * the next natural split is: ticket-detail-core.js (comments/attachments/links)
 * vs. ticket-detail-ai.js (triage/duplicates/merge).
 */

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
            const count = agentWorkload[u.username] || 0;
            opt.textContent = `${u.username} (${u.role}) — ${count} open`;
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
        showToast(agent ? `Assigned to ${agent}.` : 'Agent unassigned.', 'success');
        loadAgentWorkload();
    } catch (e) {
        console.error('Reassignment failed:', e);
    }
}

function renderDetailSlaLine(ticket) {
    const el = document.getElementById('detailSlaLine');
    if (!el) return;
    // Reuses the exact same badge language as the console table (buildSlaChipHtml,
    // defined in tickets-console.js) — SLA urgency should look identical everywhere.
    el.innerHTML = `<span style="font-size:12px; color:var(--g-text-faint); margin-right:6px;">SLA:</span>${buildSlaChipHtml(ticket)}`;
}

// Grouped primary actions — everything that moves a ticket forward (reply,
// acknowledge, resolve, pause/resume SLA, close) lives together in one row,
// instead of being scattered or (previously) missing from the detail view
// entirely — acknowledging/resolving used to only be possible from the
// console table row, so opening a ticket's full detail was a dead end.
function renderDetailActionBar(ticket) {
    const bar = document.getElementById('detailActionBar');
    if (!bar) return;

    const role = localStorage.getItem('jwt_role');
    const isAdmin = role === 'ADMIN';
    const buttons = [];

    buttons.push(`
        <button type="button" class="g-btn g-btn-outline" onclick="focusReplyBox()">
            <span class="material-symbols-rounded" style="font-size:16px;" aria-hidden="true">reply</span>
            Reply
        </button>`);

    if (['NEW', 'OPEN'].includes(ticket.status)) {
        buttons.push(`
            <button type="button" class="g-btn g-btn-primary" onclick="detailAcknowledge()">
                <span class="material-symbols-rounded" style="font-size:16px;" aria-hidden="true">check</span>
                Acknowledge
            </button>`);
    } else if (['IN_PROGRESS', 'REOPENED'].includes(ticket.status)) {
        buttons.push(`
            <button type="button" class="g-btn g-btn-primary" onclick="detailPrepResolve()">
                <span class="material-symbols-rounded" style="font-size:16px;" aria-hidden="true">task_alt</span>
                Resolve
            </button>`);
    }

    // SLA pause/resume — ADMIN only, folded into the same grouped bar rather
    // than living off on its own next to the status chips.
    if (isAdmin && !['RESOLVED', 'CLOSED'].includes(ticket.status)) {
        if (ticket.status === 'PENDING') {
            buttons.push(`
                <button type="button" class="g-btn g-btn-outline" onclick="toggleSlaPause()" style="color: var(--g-green); border-color: var(--g-green);">
                    <span class="material-symbols-rounded" style="font-size:16px;" aria-hidden="true">play_arrow</span>
                    Resume SLA
                </button>`);
        } else {
            buttons.push(`
                <button type="button" class="g-btn g-btn-outline" onclick="toggleSlaPause()">
                    <span class="material-symbols-rounded" style="font-size:16px;" aria-hidden="true">pause</span>
                    Pause SLA
                </button>`);
        }
    }

    if (isAdmin && ticket.status === 'RESOLVED') {
        buttons.push(`
            <button type="button" class="g-btn g-btn-outline" onclick="detailClose()">
                <span class="material-symbols-rounded" style="font-size:16px;" aria-hidden="true">lock</span>
                Close Ticket
            </button>`);
    }

    bar.innerHTML = buttons.join('');
}

function focusReplyBox() {
    const textarea = document.getElementById('newCommentContent');
    if (!textarea) return;
    textarea.scrollIntoView({ behavior: 'smooth', block: 'center' });
    textarea.focus();
}

async function detailAcknowledge() {
    if (!currentDetailTicketId) return;
    await startProgress(currentDetailTicketId);
    openTicketDetail(currentDetailTicketId);
}

function detailPrepResolve() {
    if (!currentDetailTicketId) return;
    prepResolve(currentDetailTicketId);
}

async function detailClose() {
    if (!currentDetailTicketId || !currentDetailTicket) return;
    try {
        const res = await fetchWithAuth(`/api/tickets/${currentDetailTicketId}`, {
            method: 'PUT', headers: authHeaders(),
            body: JSON.stringify({
                title: currentDetailTicket.title,
                description: currentDetailTicket.description,
                priority: currentDetailTicket.priority,
                status: 'CLOSED'
            })
        });
        if (res.ok) {
            showToast(`Ticket #${currentDetailTicketId} closed.`, 'success');
            refreshDataPipeline();
            loadConsolePage(consoleCurrentPage, consoleSearchQuery);
            openTicketDetail(currentDetailTicketId);
        } else {
            showToast('Failed to close ticket.', 'error');
        }
    } catch (e) {
        console.error('Failed to close ticket:', e);
        showToast('System error. Check console.', 'error');
    }
}

function renderStatusStepper(ticket) {
    const stepper = document.getElementById('statusStepper');
    if (!stepper) return;

    // Define the standard lifecycle — PENDING and REOPENED are side states shown separately
    const steps = [
        { key: 'OPEN',        label: 'Open',        icon: 'radio_button_unchecked' },
        { key: 'IN_PROGRESS', label: 'In Progress',  icon: 'pending' },
        { key: 'RESOLVED',    label: 'Resolved',     icon: 'check_circle' },
        { key: 'CLOSED',      label: 'Closed',       icon: 'lock' }
    ];

    const sideStates = { PENDING: 'Awaiting', REOPENED: 'Reopened', NEW: 'New' };
    const currentStatus = ticket.status;

    // Determine current step index
    const stepIndex = steps.findIndex(s => s.key === currentStatus);
    const isSideState = sideStates[currentStatus] !== undefined;

    let html = '';
    steps.forEach((step, i) => {
        // Line before each step (except the first)
        if (i > 0) {
            const lineClass = i <= stepIndex ? 'done' : '';
            html += `<div class="stepper-line ${lineClass}"></div>`;
        }

        let dotClass = '';
        let labelClass = '';
        let iconContent = '';

        if (isSideState) {
            // All steps before OPEN are muted, show side state badge
            dotClass = i < stepIndex ? 'done' : '';
            labelClass = i < stepIndex ? 'done' : '';
            iconContent = i < stepIndex
                ? '<span class="material-symbols-rounded" style="font-size:14px;">check</span>'
                : `<span class="material-symbols-rounded" style="font-size:14px; color:var(--g-text-muted);">${step.icon}</span>`;
        } else if (ticket.slaBreached && currentStatus !== 'RESOLVED' && currentStatus !== 'CLOSED') {
            // SLA breached and unresolved — show breached color on current step
            if (i < stepIndex) {
                dotClass = 'done'; labelClass = 'done';
                iconContent = '<span class="material-symbols-rounded" style="font-size:14px;">check</span>';
            } else if (i === stepIndex) {
                dotClass = 'breached'; labelClass = 'breached';
                iconContent = '<span class="material-symbols-rounded" style="font-size:14px;">warning</span>';
            } else {
                iconContent = `<span class="material-symbols-rounded" style="font-size:14px; color:var(--g-text-muted);">${step.icon}</span>`;
            }
        } else {
            if (i < stepIndex) {
                dotClass = 'done'; labelClass = 'done';
                iconContent = '<span class="material-symbols-rounded" style="font-size:14px;">check</span>';
            } else if (i === stepIndex) {
                dotClass = 'active'; labelClass = 'active';
                iconContent = `<span class="material-symbols-rounded" style="font-size:14px;">${step.icon}</span>`;
            } else {
                iconContent = `<span class="material-symbols-rounded" style="font-size:14px; color:var(--g-text-muted);">${step.icon}</span>`;
            }
        }

        html += `
            <div class="stepper-step">
                <div class="stepper-dot ${dotClass}">${iconContent}</div>
                <span class="stepper-label ${labelClass}">${step.label}</span>
            </div>`;
    });

    // Show side state badge if applicable
    if (isSideState) {
        const badgeColor = currentStatus === 'PENDING' ? 'var(--g-yellow)'
            : currentStatus === 'REOPENED' ? 'var(--g-red)' : 'var(--g-text-muted)';
        html += `
            <div style="margin-left:12px; white-space:nowrap;">
                <span style="font-size:11px; font-weight:600; padding:3px 10px; border-radius:100px;
                             background:${badgeColor}20; color:${badgeColor}; border:1px solid ${badgeColor};">
                    ${sideStates[currentStatus].toUpperCase()}
                </span>
            </div>`;
    }

    stepper.innerHTML = html;
}

async function toggleSlaPause() {
    if (!currentDetailTicketId) return;

    const ticket = (currentDetailTicket && currentDetailTicket.id === currentDetailTicketId)
        ? currentDetailTicket
        : currentTickets.find(t => t.id === currentDetailTicketId);
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

// --- 3b. Ticket Detail & Comments ---
async function openTicketDetail(id) {
    try {
        const response = await fetchWithAuth(`/api/tickets/${id}`, { headers: authHeaders() });
        if (!response.ok) return;
        const ticket = await response.json();
        currentDetailTicketId = ticket.id;
        currentDetailTicket = ticket;

        document.getElementById('detailTitle').innerText = ticket.title;
        document.getElementById('detailSubId').innerText = `Ticket #${ticket.id} · Raised by ${ticket.raisedBy || 'Unknown'}`;

        const statusChip = document.getElementById('detailStatusChip');
        statusChip.className = 'ticket-chip';
        if (['NEW', 'OPEN'].includes(ticket.status)) statusChip.className += ' chip-new';
        else if (ticket.status === 'IN_PROGRESS') statusChip.className += ' chip-progress';
        else if (ticket.status === 'RESOLVED') statusChip.className += ' chip-resolved';
        statusChip.innerText = ticket.status;

        const priorityChip = document.getElementById('detailPriorityChip');
        const priorityClass = ticket.priority === 'HIGH' ? 'priority-high' : ticket.priority === 'MEDIUM' ? 'priority-medium' : 'priority-low';
        priorityChip.className = 'priority-chip ' + priorityClass;
        priorityChip.innerText = ticket.priority;

        document.getElementById('detailCategoryChip').innerText = ticket.category ? ticket.category.name : 'Uncategorized';
        document.getElementById('detailDescription').innerText = ticket.description;

        renderStatusStepper(ticket);
        renderDetailSlaLine(ticket);
        renderDetailActionBar(ticket);
        const convertWrapper = document.getElementById('convertToKbWrapper');
        if (convertWrapper) {
            const isResolved = ticket.status === 'RESOLVED' || ticket.status === 'CLOSED';
            convertWrapper.style.display = isResolved ? 'block' : 'none';
        }
        renderComments(ticket.comments || []);
        loadCannedResponsesForPicker();
        startPresenceTracking(ticket.id);
        loadAttachments(ticket.id);
        loadLinkedTickets(ticket.id);
        populateAssignedAgentDropdown(ticket.assignedAgent);
        switchDetailTab('comments');
        document.getElementById('ticketDetailModal').showModal();
    } catch (e) {
        console.error('Failed to load ticket details:', e);
    }
}

async function convertTicketToKbArticle() {
    if (!currentDetailTicketId) return;
    try {
        const res = await fetchWithAuth(`/api/kb/articles/from-ticket/${currentDetailTicketId}`, {
            method: 'POST', headers: authHeaders()
        });
        if (res.ok) {
            showToast('Article created from this ticket.', 'success');
        } else {
            const err = await res.text();
            showToast(err || 'Failed to create article.', 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('System error. Check console.', 'error');
    }
}

// --- Collision detection (Phase B) ---
function startPresenceTracking(ticketId) {
    stopPresenceTracking(); // clear any previous ticket's interval first
    sendPresenceHeartbeat(ticketId); // fire immediately, don't wait 8s for the first check
    presenceHeartbeatInterval = setInterval(() => sendPresenceHeartbeat(ticketId), 8000);
}

async function sendPresenceHeartbeat(ticketId) {
    try {
        const res = await fetchWithAuth(`/api/tickets/${ticketId}/presence/heartbeat`, {
            method: 'POST', headers: authHeaders()
        });
        if (!res.ok) return;
        const data = await res.json();
        renderCollisionBanner(data.otherViewers || []);
    } catch (e) {
        console.error('Presence heartbeat failed:', e);
    }
}

function renderCollisionBanner(otherViewers) {
    const banner = document.getElementById('collisionBanner');
    if (!banner) return;
    if (!otherViewers.length) {
        banner.style.display = 'none';
        return;
    }
    const names = otherViewers.join(', ');
    const verb = otherViewers.length === 1 ? 'is' : 'are';
    banner.innerHTML = `
        <span class="material-symbols-rounded" aria-hidden="true">visibility</span>
        <span><strong>${escapeHtml(names)}</strong> ${verb} also looking at this ticket right now.</span>`;
    banner.style.display = 'flex';
}

function stopPresenceTracking() {
    if (presenceHeartbeatInterval) {
        clearInterval(presenceHeartbeatInterval);
        presenceHeartbeatInterval = null;
    }
    const banner = document.getElementById('collisionBanner');
    if (banner) banner.style.display = 'none';

    if (currentDetailTicketId) {
        // best-effort — don't block modal close on this
        fetchWithAuth(`/api/tickets/${currentDetailTicketId}/presence`, {
            method: 'DELETE', headers: authHeaders()
        }).catch(() => {});
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
    showToast('Comment posted.', 'success');
    openTicketDetail(currentDetailTicketId); // refresh the thread with the new comment
}

async function loadAttachments(ticketId) {
    const attachContainer = document.getElementById('attachmentList');
    if (!attachContainer) return; // Guard clause if the element doesn't exist

    renderSkeletonBlock(attachContainer, 2);

    try {
        const response = await fetchWithAuth(`/api/tickets/${ticketId}/attachments`, { headers: authHeaders() });
        if (response.ok) {
            const attachments = await response.json();

            if (attachments.length === 0) {
                attachContainer.innerHTML = '<span class="text-sm text-gray-500">No attachments.</span>';
                return;
            }

            attachContainer.innerHTML = ''; // Clear loading

            attachments.forEach(file => {
                const fileLink = document.createElement('a');
                // Ensure this URL matches your AttachmentController mapping
                fileLink.href = `/api/tickets/${file.ticketId}/attachments/${file.id}/download`;
                fileLink.target = '_blank';
                fileLink.className = 'attachment-chip';
                fileLink.innerHTML = `<span class="material-symbols-rounded" aria-hidden="true">attach_file</span> ${escapeHtml(file.originalFilename)}`;
                attachContainer.appendChild(fileLink);
            });
        } else {
            attachContainer.innerHTML = '<span class="text-sm text-red-500">Could not load attachments.</span>';
        }
    } catch (error) {
        console.error('Error loading attachments:', error);
        attachContainer.innerHTML = '<span class="text-sm text-red-500">Error loading files.</span>';
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
            showToast(err.error || 'Upload failed.', 'error');
        }
    } catch (e) {
        console.error('Upload error:', e);
    }

    // Reset input so the same file can be uploaded again if needed
    input.value = '';
}

// --- Insert-into-comment-box picker (used inside the ticket detail modal) ---
let cachedCannedResponsesForPicker = [];

async function loadCannedResponsesForPicker() {
    try {
        const res = await fetchWithAuth('/api/canned-responses', { headers: authHeaders() });
        cachedCannedResponsesForPicker = res.ok ? await res.json() : [];
    } catch (e) {
        console.error('Failed to load canned responses for picker:', e);
        cachedCannedResponsesForPicker = [];
    }
    const select = document.getElementById('cannedResponsePicker');
    if (!select) return;
    select.innerHTML = '<option value="">Insert canned response…</option>' +
        cachedCannedResponsesForPicker.map(r => `<option value="${r.id}">${escapeHtml(r.title)}</option>`).join('');
}

function insertCannedResponse() {
    const select = document.getElementById('cannedResponsePicker');
    const id = parseInt(select.value, 10);
    if (!id) return;
    const chosen = cachedCannedResponsesForPicker.find(r => r.id === id);
    if (!chosen) return;

    const textarea = document.getElementById('newCommentContent');
    const existing = textarea.value;
    textarea.value = existing ? existing + '\n\n' + chosen.body : chosen.body;
    select.value = ''; // reset picker so it can be re-selected later
    textarea.focus();
}

async function startProgress(id) {
    const ticket = (currentDetailTicket && currentDetailTicket.id === id) ? currentDetailTicket : currentTickets.find(t => t.id === id);
    if (!ticket) return;
    try {
        const res = await fetchWithAuth(`/api/tickets/${id}`, {
            method: 'PUT', headers: authHeaders(),
            // Preserve the original description — never overwrite incident details
            body: JSON.stringify({ title: ticket.title, description: ticket.description, priority: ticket.priority, status: 'IN_PROGRESS' })
        });
        if (res.ok) {
            showToast(`Ticket #${id} acknowledged — now in progress.`, 'success');
        } else {
            showToast(`Failed to acknowledge ticket #${id}.`, 'error');
        }
    } catch (e) {
        console.error('Failed to start progress on ticket:', e);
        showToast('System error. Check console.', 'error');
    }
    refreshDataPipeline();
    loadConsolePage(consoleCurrentPage, consoleSearchQuery);
}

function prepResolve(id) {
    const ticket = (currentDetailTicket && currentDetailTicket.id === id) ? currentDetailTicket : currentTickets.find(t => t.id === id);
    if (!ticket) return;
    ticketIdPendingResolve = id;
    document.getElementById('resTitle').innerText = ticket.title;
    document.getElementById('resPriority').innerText = ticket.priority;
    document.getElementById('resolveModal').showModal();
}

async function submitResolve() {
    if (!ticketIdPendingResolve) return;
    const ticket = (currentDetailTicket && currentDetailTicket.id === ticketIdPendingResolve) ? currentDetailTicket : currentTickets.find(t => t.id === ticketIdPendingResolve);
    if (!ticket) return;

    const notes   = document.getElementById('resolveNotes').value.trim();
    const comment = document.getElementById('resolveComment').value.trim();
    const isInternal = document.getElementById('resolveCommentInternal').checked;

    const finalDescription = notes
        ? ticket.description + '\n\n--- Resolution Notes ---\n' + notes
        : ticket.description;

    // 1. Resolve the ticket
    await fetchWithAuth(`/api/tickets/${ticketIdPendingResolve}`, {
        method: 'PUT', headers: authHeaders(),
        body: JSON.stringify({
            title: ticket.title,
            description: finalDescription,
            priority: ticket.priority,
            status: 'RESOLVED'
        })
    });

    // 2. Post resolution comment if provided
    if (comment) {
        await fetchWithAuth(`/api/tickets/${ticketIdPendingResolve}/comments`, {
            method: 'POST', headers: authHeaders(),
            body: JSON.stringify({ content: comment, isInternal })
        });
    }

    document.getElementById('resolveModal').close();
    document.getElementById('resolveNotes').value   = '';
    document.getElementById('resolveComment').value = '';
    document.getElementById('resolveCommentInternal').checked = false;
    const resolvedId = ticketIdPendingResolve;
    ticketIdPendingResolve = null;
    showToast('Ticket resolved and comment posted.', 'success');
    refreshDataPipeline();
    loadConsolePage(consoleCurrentPage, consoleSearchQuery);
    if (currentDetailTicketId === resolvedId) openTicketDetail(resolvedId);
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
    renderSkeletonBlock(historyList, 3);

    try {
        const res = await fetchWithAuth(`/api/tickets/${ticketId}/history`, {
            headers: authHeaders()
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

// --- Duplicate detection + merge (Phase D) ---
function showTriageOutput(html) {
    const panel = document.getElementById('triageOutputPanel');
    panel.innerHTML = html;
    panel.style.display = 'block';
}

async function triageCheckDuplicates() {
    showTriageOutput('Checking for duplicates...');
    try {
        const res = await fetchWithAuth(`/api/tickets/${currentDetailTicketId}/triage/duplicates`, { headers: authHeaders() });
        const tickets = await res.json();
        if (!tickets.length) { showTriageOutput('No likely duplicates found.'); return; }

        showTriageOutput('<strong>Possible duplicates:</strong><br>' +
            tickets.map(t => `
                <div style="display:flex; justify-content:space-between; align-items:center; padding:6px 0;">
                    <span>#${t.id} — ${escapeHtml(t.title)}</span>
                    <button class="g-btn" style="font-size:11px; padding:4px 10px;"
                            onclick="mergeDuplicateTicket(${t.id})">Merge into this ticket</button>
                </div>
            `).join(''));
    } catch (e) {
        console.error(e);
        showTriageOutput('Failed to check duplicates.');
    }
}

async function mergeDuplicateTicket(duplicateId) {
    if (!confirm(`Merge ticket #${duplicateId} into #${currentDetailTicketId}? #${duplicateId} will be closed.`)) return;
    try {
        const res = await fetchWithAuth(`/api/tickets/${duplicateId}/merge-into/${currentDetailTicketId}`, {
            method: 'POST', headers: authHeaders()
        });
        if (res.ok) {
            showToast(`Ticket #${duplicateId} merged and closed.`, 'success');
            triageCheckDuplicates(); // refresh the list
        } else {
            const err = await res.text();
            showToast(err || 'Merge failed.', 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('System error. Check console.', 'error');
    }
}