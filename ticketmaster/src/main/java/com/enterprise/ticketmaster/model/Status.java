package com.enterprise.ticketmaster.model;

public enum Status {
    NEW,           // Unassigned, fresh in the queue
    OPEN,          // Assigned, awaiting action
    IN_PROGRESS,   // Actively being worked on
    PENDING,       // Waiting on the customer or third-party (SLA clock pauses here)
    RESOLVED,      // Agent believes it is fixed
    CLOSED,        // Customer confirmed it is fixed (or auto-closed after 3 days)
    REOPENED       // Customer rejected the fix
}