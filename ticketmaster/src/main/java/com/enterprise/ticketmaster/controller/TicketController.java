package com.enterprise.ticketmaster.controller;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.enterprise.ticketmaster.model.Priority;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @GetMapping
    public List<Ticket> getAllTickets() {
        return ticketService.getAllTickets();
    }

    @PostMapping
    public ResponseEntity<Ticket> createTicket(@Valid @RequestBody Ticket ticket, Authentication authentication) {
        if (authentication != null) {
            ticket.setRaisedBy(authentication.getName()); // Automatically grabs the logged-in username
        }
        // Set a default priority level if none was sent in the raw JSON payload
        if (ticket.getPriority() == null) {
            ticket.setPriority(Priority.MEDIUM);
        }
        return new ResponseEntity<>(ticketService.createTicket(ticket), HttpStatus.CREATED);
    }

    @GetMapping("/status/{status}")
    public List<Ticket> getTicketsByStatus(@PathVariable String status) {
        return ticketService.getTicketsByStatus(status);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ticket> updateTicket(@PathVariable Long id, @Valid @RequestBody Ticket updatedTicket, Authentication authentication) {
        if (authentication != null && "RESOLVED".equals(updatedTicket.getStatus())) {
            updatedTicket.setResolvedBy(authentication.getName()); // Tracks which admin signed off on it
        }
        return ResponseEntity.ok(ticketService.updateTicket(id, updatedTicket));
    }

    @DeleteMapping("/{id}")
    public String deleteTicket(@PathVariable Long id) {
        // 💡 Changed lowercase 's' to capital 'S'
        ticketService.deleteTicket(id);
        return "Ticket with ID " + id + " has been successfully deleted from the database.";
    }
}
