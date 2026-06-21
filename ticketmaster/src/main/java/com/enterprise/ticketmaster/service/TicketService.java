package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.stereotype.Service;
import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final RoutingEngineService routingEngine;
    private final SlaManagementService slaEngine;
    private final CategoryRepository categoryRepository;

    public TicketService(TicketRepository ticketRepository, RoutingEngineService routingEngine, SlaManagementService slaEngine, CategoryRepository categoryRepository) {
        this.ticketRepository = ticketRepository;
        this.routingEngine = routingEngine;
        this.slaEngine = slaEngine;
        this.categoryRepository = categoryRepository;
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
        return ticketRepository.save(ticket);
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
            Status newStatus = updatedTicketDetails.getStatus();

            existingTicket.setTitle(updatedTicketDetails.getTitle());
            existingTicket.setDescription(updatedTicketDetails.getDescription());
            existingTicket.setStatus(newStatus);

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
            return ticketRepository.save(existingTicket);
        }).orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ticket not found with id: " + id);
        }
        ticketRepository.deleteById(id);
    }
}