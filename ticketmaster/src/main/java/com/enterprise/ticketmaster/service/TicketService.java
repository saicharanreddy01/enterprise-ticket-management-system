package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.*;
import com.enterprise.ticketmaster.repository.TicketRepository;
import com.enterprise.ticketmaster.service.RoutingEngineService;
import com.enterprise.ticketmaster.service.SlaManagementService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.event.TicketEvent;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.enterprise.ticketmaster.repository.TicketHistoryRepository;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final RoutingEngineService routingEngine;
    private final SlaManagementService slaEngine;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TicketHistoryRepository ticketHistoryRepository;

    public TicketService(TicketRepository ticketRepository, RoutingEngineService routingEngine,
                         SlaManagementService slaEngine, CategoryRepository categoryRepository,
                         ApplicationEventPublisher eventPublisher,
                         TicketHistoryRepository ticketHistoryRepository) {
        this.ticketRepository = ticketRepository;
        this.routingEngine = routingEngine;
        this.slaEngine = slaEngine;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
        this.ticketHistoryRepository = ticketHistoryRepository;
    }

    public Ticket createTicket(Ticket ticket) {
        // Resolve category from DB using the id the frontend sent
        // Without this, Hibernate sees a detached Category object and crashes
        if (ticket.getCategory() != null && ticket.getCategory().getId() != null) {
            Category category = categoryRepository.findById(ticket.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + ticket.getCategory().getId()));
            ticket.setCategory(category);
        }
        routingEngine.routeTicket(ticket);
        slaEngine.assignSla(ticket);
        // Once routed to a queue, the ticket is no longer "fresh/unassigned" — it's OPEN
        ticket.setStatus(Status.OPEN);
        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketEvent(saved, NotificationType.TICKET_CREATED));
        return saved;
    }

    // Fetch all records from the database table
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAllWithDetails();
    }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    public List<Ticket> getTicketsByStatus(Status status) {
        return ticketRepository.findByStatusWithDetails(status);
    }

    public Ticket updateTicket(Long id, Ticket updatedTicketDetails, String resolvedBy) {
        return ticketRepository.findById(id).map(existingTicket -> {
            Status oldStatus = existingTicket.getStatus();
            Priority oldPriority = existingTicket.getPriority();
            String oldAssignedTo = existingTicket.getAssignedTo();
            Category oldCategory = existingTicket.getCategory();
            Status newStatus = updatedTicketDetails.getStatus();

            existingTicket.setTitle(updatedTicketDetails.getTitle());
            existingTicket.setDescription(updatedTicketDetails.getDescription());
            existingTicket.setStatus(newStatus);

            if (newStatus == Status.RESOLVED) {
                existingTicket.setResolvedAt(LocalDateTime.now());
            } else if (newStatus == Status.REOPENED) {
                existingTicket.setResolvedAt(null);
            }

            // SLA pause/resume: time spent waiting on the customer shouldn't count against the deadline
            if (newStatus == Status.PENDING && oldStatus != Status.PENDING) {
                // Entering a pause — record when it started
                existingTicket.setPausedAt(LocalDateTime.now());
            } else if (oldStatus == Status.PENDING && newStatus != Status.PENDING && existingTicket.getPausedAt() != null) {
                // Resuming — push the deadline back by exactly how long it was paused
                Duration pausedDuration = Duration.between(existingTicket.getPausedAt(), LocalDateTime.now());
                if (existingTicket.getSlaDueDate() != null) {
                    existingTicket.setSlaDueDate(existingTicket.getSlaDueDate().plus(pausedDuration));
                }
                existingTicket.setPausedAt(null);
            }

            // Business logic lives here: if resolved, record who signed off
            if (Status.RESOLVED.equals(newStatus) && resolvedBy != null) {
                existingTicket.setResolvedBy(resolvedBy);
            }

            // --- Audit history recording ---
            LocalDateTime now = LocalDateTime.now();

            if (updatedTicketDetails.getStatus() != null && updatedTicketDetails.getStatus() != oldStatus) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("status");
                h.setOldValue(oldStatus != null ? oldStatus.name() : null);
                h.setNewValue(updatedTicketDetails.getStatus().name());
                ticketHistoryRepository.save(h);
            }

            if (updatedTicketDetails.getPriority() != null && updatedTicketDetails.getPriority() != oldPriority) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("priority");
                h.setOldValue(oldPriority != null ? oldPriority.name() : null);
                h.setNewValue(updatedTicketDetails.getPriority().name());
                ticketHistoryRepository.save(h);
            }

            if (updatedTicketDetails.getCategory() != null && oldCategory != null
                    && !updatedTicketDetails.getCategory().getId().equals(oldCategory.getId())) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("category");
                h.setOldValue(oldCategory.getName());
                h.setNewValue(updatedTicketDetails.getCategory().getName());
                ticketHistoryRepository.save(h);
            }

            if (updatedTicketDetails.getAssignedTo() != null
                    && !updatedTicketDetails.getAssignedTo().equals(oldAssignedTo)) {
                TicketHistory h = new TicketHistory();
                h.setTicketId(id);
                h.setChangedBy(resolvedBy);
                h.setChangedAt(now);
                h.setFieldName("assignedTo");
                h.setOldValue(oldAssignedTo);
                h.setNewValue(updatedTicketDetails.getAssignedTo());
                ticketHistoryRepository.save(h);
            }
// --- End audit history ---

            Ticket saved = ticketRepository.save(existingTicket);

            if (newStatus == Status.REOPENED && oldStatus != Status.REOPENED) {
                eventPublisher.publishEvent(new TicketEvent(saved, NotificationType.TICKET_REOPENED));
            }

            return saved;
        }).orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ticket not found with id: " + id);
        }
        ticketRepository.deleteById(id);
    }
}