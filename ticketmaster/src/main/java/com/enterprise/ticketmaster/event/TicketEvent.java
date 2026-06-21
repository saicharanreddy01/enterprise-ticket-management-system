package com.enterprise.ticketmaster.event;

import com.enterprise.ticketmaster.model.NotificationType;
import com.enterprise.ticketmaster.model.Ticket;

// A plain POJO event — Spring has supported arbitrary objects as events since 4.2,
// no need to extend ApplicationEvent anymore.
public class TicketEvent {
    private final Ticket ticket;
    private final NotificationType type;

    public TicketEvent(Ticket ticket, NotificationType type) {
        this.ticket = ticket;
        this.type = type;
    }

    public Ticket getTicket() { return ticket; }
    public NotificationType getType() { return type; }
}