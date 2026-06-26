package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.TicketLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketLinkRepository extends JpaRepository<TicketLink, Long> {

    @Query("SELECT l FROM TicketLink l WHERE l.sourceTicketId = :ticketId OR l.targetTicketId = :ticketId")
    List<TicketLink> findAllLinksForTicket(@Param("ticketId") Long ticketId);

    @Query("SELECT COUNT(l) > 0 FROM TicketLink l WHERE " +
            "(l.sourceTicketId = :src AND l.targetTicketId = :tgt) OR " +
            "(l.sourceTicketId = :tgt AND l.targetTicketId = :src)")
    boolean linkExists(@Param("src") Long src, @Param("tgt") Long tgt);
}