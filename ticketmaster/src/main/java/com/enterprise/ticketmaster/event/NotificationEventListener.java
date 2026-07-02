package com.enterprise.ticketmaster.event;

import com.enterprise.ticketmaster.model.Notification;
import com.enterprise.ticketmaster.model.NotificationType;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.NotificationRepository;
import com.enterprise.ticketmaster.repository.UserRepository;
import com.enterprise.ticketmaster.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Value("${app.mail.notify:}")
    private String notifyEmail;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false)
                                     EmailService emailService,
                                     UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @EventListener
    public void handleTicketEvent(TicketEvent event) {
        Ticket ticket = event.getTicket();
        Notification notification = new Notification();
        notification.setType(event.getType());
        notification.setRelatedTicketId(ticket.getId());

        Long ticketId  = ticket.getId();
        String title   = ticket.getTitle();

        switch (event.getType()) {
            case TICKET_CREATED -> {
                notification.setMessage("New ticket #" + ticketId + " raised: " + title);
                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendTicketCreated(notifyEmail, ticketId, title);
                }
            }
            case SLA_WARNING -> {
                notification.setMessage("⚠️ SLA warning on ticket #" + ticketId
                        + ": 25% of time remaining — " + title);
                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendSlaWarning(notifyEmail, ticketId, title);
                }
            }
            case SLA_CRITICAL -> {
                notification.setMessage("🔴 SLA critical on ticket #" + ticketId
                        + ": 10% of time remaining — " + title);
                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendSlaCritical(notifyEmail, ticketId, title);
                }
            }
            case SLA_BREACHED -> {
                notification.setMessage("SLA breached on ticket #" + ticketId + ": " + title);
                if (emailService != null && !notifyEmail.isBlank()) {
                    emailService.sendSlaBreached(notifyEmail, ticketId, title);
                }
            }
            case TICKET_ASSIGNED -> {
                notification.setMessage("Ticket #" + ticketId + " has been assigned to you: " + title);

                String assignedUsername = ticket.getAssignedAgent();
                if (assignedUsername != null && !assignedUsername.isBlank()) {
                    userRepository.findByUsername(assignedUsername).ifPresent(user -> {
                        String agentEmail = user.getEmail();
                        if (emailService != null && agentEmail != null && !agentEmail.isBlank()) {
                            emailService.sendTicketAssignmentEmail(agentEmail, ticket);
                        }
                    });
                }
            }
            case TICKET_REOPENED ->
                    notification.setMessage("Ticket #" + ticketId + " reopened: " + title);
            case TICKET_AUTO_CLOSED ->
                    notification.setMessage("Ticket #" + ticketId + " was automatically closed after 3 days in Resolved status.");
        }

        notificationRepository.save(notification);
    }
}