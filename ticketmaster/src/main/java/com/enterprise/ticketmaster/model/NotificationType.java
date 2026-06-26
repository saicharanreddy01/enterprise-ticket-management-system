package com.enterprise.ticketmaster.model;

public enum NotificationType {
    TICKET_CREATED,
    TICKET_REOPENED,
    TICKET_AUTO_CLOSED,
    SLA_WARNING,
    SLA_CRITICAL,
    SLA_BREACHED
}