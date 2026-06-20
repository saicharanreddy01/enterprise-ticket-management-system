package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // JOIN FETCH pulls category, subCategory, and comments in via SQL JOINs
    // instead of Hibernate firing a separate query per ticket for each (N+1 fix).
    // DISTINCT is required because joining a one-to-many (comments) duplicates
    // the ticket row per comment at the SQL level — Hibernate collapses those
    // duplicates back into one Java object per ticket when DISTINCT is present.
    @Query("SELECT DISTINCT t FROM Ticket t " +
            "LEFT JOIN FETCH t.category " +
            "LEFT JOIN FETCH t.subCategory " +
            "LEFT JOIN FETCH t.comments " +
            "ORDER BY t.id DESC")
    List<Ticket> findAllWithDetails();

    @Query("SELECT DISTINCT t FROM Ticket t " +
            "LEFT JOIN FETCH t.category " +
            "LEFT JOIN FETCH t.subCategory " +
            "LEFT JOIN FETCH t.comments " +
            "WHERE t.status = :status " +
            "ORDER BY t.id DESC")
    List<Ticket> findByStatusWithDetails(@Param("status") Status status);

    // Finds active tickets that just missed their deadline
    java.util.List<Ticket> findBySlaBreachedFalseAndStatusNotInAndSlaDueDateBefore(
            java.util.Collection<Status> statuses,
            java.time.LocalDateTime now
    );
}