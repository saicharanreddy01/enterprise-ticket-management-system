package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Spring Boot parses this method name automatically and writes:
    // "SELECT * FROM tickets WHERE status = ?" under the hood!
    List<Ticket> findByStatus(Status status);

    // Finds active tickets that just missed their deadline
    java.util.List<Ticket> findBySlaBreachedFalseAndStatusNotInAndSlaDueDateBefore(
            java.util.Collection<Status> statuses,
            java.time.LocalDateTime now
    );
}
