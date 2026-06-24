package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.TicketHistory;
import com.enterprise.ticketmaster.repository.TicketHistoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketHistoryController {

    private final TicketHistoryRepository ticketHistoryRepository;

    public TicketHistoryController(TicketHistoryRepository ticketHistoryRepository) {
        this.ticketHistoryRepository = ticketHistoryRepository;
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<TicketHistory>> getTicketHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ticketHistoryRepository.findByTicketIdOrderByChangedAtDesc(id));
    }
}