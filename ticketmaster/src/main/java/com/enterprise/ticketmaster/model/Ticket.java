package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@EntityListeners(AuditingEntityListener.class) // Enables automated clock auditing capture
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Issue title cannot be blank")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    @Column(nullable = false, length = 100)
    private String title;

    @NotBlank(message = "Detailed description cannot be blank")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM; // Integrates our data type safety Enum

    @Column(nullable = false, updatable = false)
    private String raisedBy; // Captures the identity of the ticket creator

    private String resolvedBy; // Tracks which administrator resolved the issue

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // Set automatically by JPA Auditing

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // Maintained automatically by JPA Auditing

    // ==================== CONSTRUCTORS ====================

    public Ticket() {
        // Default zero-argument constructor required natively by Hibernate ORM
    }

    public Ticket(String title, String description, Priority priority) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = Status.OPEN;
    }

    // ==================== GETTERS AND SETTERS ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getRaisedBy() {
        return raisedBy;
    }

    public void setRaisedBy(String raisedBy) {
        this.raisedBy = raisedBy;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}