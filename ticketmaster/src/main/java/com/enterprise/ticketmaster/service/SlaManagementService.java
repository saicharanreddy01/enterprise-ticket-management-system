package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.event.TicketEvent;
import com.enterprise.ticketmaster.model.NotificationType;
import com.enterprise.ticketmaster.model.Priority;
import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import org.springframework.transaction.annotation.Transactional;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class SlaManagementService {
    private static final Logger log = LoggerFactory.getLogger(SlaManagementService.class);

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SlaManagementService(TicketRepository ticketRepository, ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
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
    @Scheduled(cron = "0 0 * * * *")
    public void monitorSlaBreaches() {
        // We do NOT penalize agents if the ticket is waiting on the customer (PENDING)
        List<Status> excludedStatuses = Arrays.asList(Status.RESOLVED, Status.CLOSED, Status.PENDING);

        // Fetch all active tickets whose due date is in the past
        List<Ticket> breachedTickets = ticketRepository
                .findBySlaBreachedFalseAndStatusNotInAndSlaDueDateBefore(excludedStatuses, LocalDateTime.now());

        if (!breachedTickets.isEmpty()) {
            for (Ticket ticket : breachedTickets) {
                ticket.setSlaBreached(true);
                System.out.println("SLA BREACH DETECTED: Ticket ID " + ticket.getId() + " assigned to " + ticket.getAssignedTo());
            }
            // Save all the breached statuses to the database in one batch
            ticketRepository.saveAll(breachedTickets);
            // Now that they're persisted, fire an event per ticket so the notification feed picks it up —
            // this replaces the old "Future Enhancement" comment that used to sit here
            for (Ticket ticket : breachedTickets) {
                eventPublisher.publishEvent(new TicketEvent(ticket, NotificationType.SLA_BREACHED));
            }
        }
    }

    @Scheduled(fixedRate = 10_000)
    @Transactional
    public void autoCloseResolvedTickets() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        List<Ticket> candidates = ticketRepository.findAutoCloseCandidates(Status.RESOLVED, cutoff);

        if (candidates.isEmpty()) return;

        log.info("Auto-close scheduler: {} ticket(s) eligible for closure", candidates.size());

        for (Ticket ticket : candidates) {
            ticket.setStatus(Status.CLOSED);
            ticket.setResolvedAt(null);   // closed — field no longer meaningful
            ticketRepository.save(ticket);
            eventPublisher.publishEvent(
                    new TicketEvent(ticket, NotificationType.TICKET_AUTO_CLOSED)
            );
        }
    }
}