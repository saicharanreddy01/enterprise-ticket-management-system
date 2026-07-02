package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.enterprise.ticketmaster.model.Priority;

import java.time.LocalDateTime;
import java.util.Collection;
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
            "WHERE (LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR (:numericQ IS NOT NULL AND t.id = :numericQ)) ORDER BY t.id DESC",
            countQuery = "SELECT COUNT(t) FROM Ticket t " +
                    "WHERE (LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                    "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) " +
                    "OR (:numericQ IS NOT NULL AND t.id = :numericQ))")
    Page<Ticket> searchTickets(@Param("q") String q,
                               @Param("numericQ") Long numericQ,
                               Pageable pageable);

    @Query(value = "SELECT t FROM Ticket t LEFT JOIN FETCH t.category LEFT JOIN FETCH t.subCategory " +
            "WHERE ((:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "OR (:numericQ IS NOT NULL AND t.id = :numericQ)) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
            "AND (:raisedBy IS NULL OR t.raisedBy = :raisedBy) " +
            "AND (:assignedAgent IS NULL OR t.assignedAgent = :assignedAgent) " +
            "ORDER BY t.id DESC",
            countQuery = "SELECT COUNT(t) FROM Ticket t " +
                    "WHERE ((:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                    "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                    "OR (:numericQ IS NOT NULL AND t.id = :numericQ)) " +
                    "AND (:status IS NULL OR t.status = :status) " +
                    "AND (:priority IS NULL OR t.priority = :priority) " +
                    "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
                    "AND (:raisedBy IS NULL OR t.raisedBy = :raisedBy) " +
                    "AND (:assignedAgent IS NULL OR t.assignedAgent = :assignedAgent)")
    Page<Ticket> searchWithFilters(
            @Param("q") String q,
            @Param("numericQ") Long numericQ,
            @Param("status") Status status,
            @Param("priority") Priority priority,
            @Param("categoryId") Long categoryId,
            @Param("raisedBy") String raisedBy,
            @Param("assignedAgent") String assignedAgent,
            Pageable pageable);

    @Query(value = "SELECT t FROM Ticket t LEFT JOIN FETCH t.category LEFT JOIN FETCH t.subCategory " +
            "WHERE ((:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "OR (:numericQ IS NOT NULL AND t.id = :numericQ)) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
            "AND (:raisedBy IS NULL OR t.raisedBy = :raisedBy) " +
            "AND (:assignedAgent IS NULL OR t.assignedAgent = :assignedAgent)",
            countQuery = "SELECT COUNT(t) FROM Ticket t " +
                    "WHERE ((:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                    "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))) " +
                    "OR (:numericQ IS NOT NULL AND t.id = :numericQ)) " +
                    "AND (:status IS NULL OR t.status = :status) " +
                    "AND (:priority IS NULL OR t.priority = :priority) " +
                    "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
                    "AND (:raisedBy IS NULL OR t.raisedBy = :raisedBy) " +
                    "AND (:assignedAgent IS NULL OR t.assignedAgent = :assignedAgent)")
    Page<Ticket> searchWithFiltersSorted(
            @Param("q") String q,
            @Param("numericQ") Long numericQ,
            @Param("status") Status status,
            @Param("priority") Priority priority,
            @Param("categoryId") Long categoryId,
            @Param("raisedBy") String raisedBy,
            @Param("assignedAgent") String assignedAgent,
            Pageable pageable);

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

    List<Ticket> findBySlaBreachedFalseAndStatusNotInAndSlaDueDateBefore(
            Collection<Status> statuses,
            LocalDateTime now
    );

    @Query("SELECT t FROM Ticket t WHERE t.slaBreached = false " +
            "AND t.slaDueDate IS NOT NULL " +
            "AND t.status NOT IN :excludedStatuses")
    List<Ticket> findActiveTicketsWithSla(@Param("excludedStatuses") List<Status> excludedStatuses);

    @Query(value = """
        SELECT t.assigned_agent,
               COUNT(*) AS totalAssigned,
               SUM(CASE WHEN t.status IN ('RESOLVED','CLOSED') THEN 1 ELSE 0 END) AS totalResolved,
               SUM(CASE WHEN t.sla_breached = 0 THEN 1 ELSE 0 END) AS slaCompliant
        FROM tickets t
        WHERE t.assigned_agent IS NOT NULL
        GROUP BY t.assigned_agent
        """, nativeQuery = true)
    List<Object[]> findAgentAssignmentStats();

    @Query(value = "SELECT t.assigned_agent, COUNT(*) FROM tickets t " +
            "WHERE t.assigned_agent IS NOT NULL " +
            "AND t.status NOT IN ('RESOLVED', 'CLOSED') " +
            "GROUP BY t.assigned_agent", nativeQuery = true)
    List<Object[]> findOpenTicketCountsByAgent();

    @Query(value = """
        SELECT t.resolved_by,
               AVG(TIMESTAMPDIFF(MINUTE, t.created_at, t.resolved_at)) AS avgResolutionMinutes
        FROM tickets t
        WHERE t.resolved_by IS NOT NULL
          AND t.resolved_at IS NOT NULL
        GROUP BY t.resolved_by
        """, nativeQuery = true)
    List<Object[]> findAvgResolutionPerAgent();

    @Query(value = """
        SELECT DATE(created_at) AS day, COUNT(*) AS count
        FROM tickets
        WHERE created_at >= :from
        GROUP BY DATE(created_at)
        ORDER BY day ASC
        """, nativeQuery = true)
    List<Object[]> findDailyVolume(@Param("from") LocalDateTime from);
}