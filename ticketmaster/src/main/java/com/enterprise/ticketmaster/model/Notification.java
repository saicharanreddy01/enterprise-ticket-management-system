package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // Deliberately NOT a JPA relationship to Ticket — notifications are a decoupled
    // read model that shouldn't block or cascade if the related ticket is later deleted.
    @Column(name = "related_ticket_id")
    private Long relatedTicketId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getRelatedTicketId() { return relatedTicketId; }
    public void setRelatedTicketId(Long relatedTicketId) { this.relatedTicketId = relatedTicketId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}