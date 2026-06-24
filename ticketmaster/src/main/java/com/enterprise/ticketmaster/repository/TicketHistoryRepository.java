package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {
    List<TicketHistory> findByTicketIdOrderByChangedAtDesc(Long ticketId);
}