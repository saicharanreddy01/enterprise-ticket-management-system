package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Priority;
import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class SlaManagementService {

    private final TicketRepository ticketRepository;

    public SlaManagementService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // Assigns the initial deadline based on severity
    public void assignSla(Ticket ticket) {
        LocalDateTime now = LocalDateTime.now();
        if (ticket.getPriority() == Priority.HIGH) {
            ticket.setSlaDueDate(now.plusHours(4));   // 4 Hour SLA
        } else if (ticket.getPriority() == Priority.MEDIUM) {
            ticket.setSlaDueDate(now.plusHours(24));  // 24 Hour SLA
        } else {
            ticket.setSlaDueDate(now.plusHours(72));  // 3 Day SLA
        }
    }

    // The Background Worker: Runs every 60,000 milliseconds (1 minute)
    @Scheduled(fixedRate = 60000)
    public void monitorSlaBreaches() {
        // We do NOT penalize agents if the ticket is waiting on the customer (PENDING)
        List<Status> excludedStatuses = Arrays.asList(Status.RESOLVED, Status.CLOSED, Status.PENDING);

        // Fetch all active tickets whose due date is in the past
        List<Ticket> breachedTickets = ticketRepository
                .findBySlaBreachedFalseAndStatusNotInAndSlaDueDateBefore(excludedStatuses, LocalDateTime.now());

        if (!breachedTickets.isEmpty()) {
            for (Ticket ticket : breachedTickets) {
                ticket.setSlaBreached(true);
                // Future Enhancement: We will trigger a real-time Email or WebSocket alert here!
                System.out.println("SLA BREACH DETECTED: Ticket ID " + ticket.getId() + " assigned to " + ticket.getAssignedTo());
            }
            // Save all the breached statuses to the database in one batch
            ticketRepository.saveAll(breachedTickets);
        }
    }
}