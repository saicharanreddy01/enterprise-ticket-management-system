package com.enterprise.ticketmaster.event;

import com.enterprise.ticketmaster.model.Notification;
import com.enterprise.ticketmaster.repository.NotificationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;

    public NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void handleTicketEvent(TicketEvent event) {
        Notification notification = new Notification();
        notification.setType(event.getType());
        notification.setRelatedTicketId(event.getTicket().getId());

        switch (event.getType()) {
            case TICKET_CREATED -> notification.setMessage("New ticket #" + event.getTicket().getId() + " raised: " + event.getTicket().getTitle());
            case SLA_BREACHED -> notification.setMessage("SLA breached on ticket #" + event.getTicket().getId() + ": " + event.getTicket().getTitle());
            case TICKET_REOPENED -> notification.setMessage("Ticket #" + event.getTicket().getId() + " reopened: " + event.getTicket().getTitle());
        }

        notificationRepository.save(notification);
    }
}