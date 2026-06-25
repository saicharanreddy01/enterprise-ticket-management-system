package com.enterprise.ticketmaster.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("SELECT DISTINCT t FROM Ticket t " +
            "LEFT JOIN FETCH t.category " +
            "LEFT JOIN FETCH t.subCategory " +
            "LEFT JOIN FETCH t.comments " +
            "ORDER BY t.id DESC")
    List<Ticket> findAllWithDetails();

    @Query(value = "SELECT t FROM Ticket t LEFT JOIN FETCH t.category LEFT JOIN FETCH t.subCategory ORDER BY t.id DESC",
            countQuery = "SELECT COUNT(t) FROM Ticket t")
    Page<Ticket> findAllPaginated(Pageable pageable);

    @Query(value = "SELECT t FROM Ticket t LEFT JOIN FETCH t.category LEFT JOIN FETCH t.subCategory " +
            "WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY t.id DESC",
            countQuery = "SELECT COUNT(t) FROM Ticket t WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                    "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Ticket> searchTickets(@Param("q") String q, Pageable pageable);

    @Query("SELECT DISTINCT t FROM Ticket t " +
            "LEFT JOIN FETCH t.category " +
            "LEFT JOIN FETCH t.subCategory " +
            "LEFT JOIN FETCH t.comments " +
            "WHERE t.status = :status " +
            "ORDER BY t.id DESC")
    List<Ticket> findByStatusWithDetails(@Param("status") Status status);

    @Query("SELECT t FROM Ticket t WHERE t.status = :status " +
            "AND t.resolvedAt IS NOT NULL " +
            "AND t.resolvedAt < :cutoff")
    List<Ticket> findAutoCloseCandidates(@Param("status") Status status,
                                         @Param("cutoff") LocalDateTime cutoff);

    // Finds active tickets that just missed their deadline
    java.util.List<Ticket> findBySlaBreachedFalseAndStatusNotInAndSlaDueDateBefore(
            java.util.Collection<Status> statuses,
            java.time.LocalDateTime now
    );
}