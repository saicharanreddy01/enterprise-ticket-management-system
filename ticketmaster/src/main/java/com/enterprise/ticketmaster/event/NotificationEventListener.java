package com.enterprise.ticketmaster.event;

import com.enterprise.ticketmaster.model.Notification;
import com.enterprise.ticketmaster.repository.NotificationRepository;
import com.enterprise.ticketmaster.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    @Value("${app.mail.notify}")
    private String notifyEmail;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                     EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    @EventListener
    public void handleTicketEvent(TicketEvent event) {
        Notification notification = new Notification();
        notification.setType(event.getType());
        notification.setRelatedTicketId(event.getTicket().getId());

        Long ticketId  = event.getTicket().getId();
        String title   = event.getTicket().getTitle();
        String resolvedBy = event.getTicket().getResolvedBy();

        switch (event.getType()) {
            case TICKET_CREATED -> {
                notification.setMessage("New ticket #" + ticketId + " raised: " + title);
                emailService.sendTicketCreated(notifyEmail, ticketId, title);
            }
            case SLA_BREACHED -> {
                notification.setMessage("SLA breached on ticket #" + ticketId + ": " + title);
                emailService.sendSlaBreached(notifyEmail, ticketId, title);
            }
            case SLA_WARNING -> {
                notification.setMessage("⚠️ SLA warning on ticket #" + ticketId
                        + ": 25% of time remaining — " + title);
                emailService.sendSlaWarning(notifyEmail, ticketId, title);
            }
            case SLA_CRITICAL -> {
                notification.setMessage("🔴 SLA critical on ticket #" + ticketId
                        + ": 10% of time remaining — " + title);
                emailService.sendSlaCritical(notifyEmail, ticketId, title);
            }
            case TICKET_REOPENED ->
                    notification.setMessage("Ticket #" + ticketId + " reopened: " + title);
            case TICKET_AUTO_CLOSED ->
                    notification.setMessage("Ticket #" + ticketId + " was automatically closed after 3 days in Resolved status.");
        }

        notificationRepository.save(notification);
    }
}