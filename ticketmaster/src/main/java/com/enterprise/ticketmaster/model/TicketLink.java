package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_links")
public class TicketLink {

    public enum LinkType {
        BLOCKS,       // This ticket is blocking the target
        RELATED_TO,   // General relationship
        DUPLICATES    // This ticket duplicates the target
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_ticket_id", nullable = false)
    private Long sourceTicketId;

    @Column(name = "target_ticket_id", nullable = false)
    private Long targetTicketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    private LinkType linkType;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public Long getSourceTicketId() { return sourceTicketId; }
    public void setSourceTicketId(Long sourceTicketId) { this.sourceTicketId = sourceTicketId; }

    public Long getTargetTicketId() { return targetTicketId; }
    public void setTargetTicketId(Long targetTicketId) { this.targetTicketId = targetTicketId; }

    public LinkType getLinkType() { return linkType; }
    public void setLinkType(LinkType linkType) { this.linkType = linkType; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}