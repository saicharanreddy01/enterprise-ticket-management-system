package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import java.util.List;

@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository; // Plugs in our database interface!

    // Save a ticket directly into the database table
    public Ticket createTicket(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    // Fetch all records from the database table
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public List<Ticket> getTicketsByStatus(String status) {
        return ticketRepository.findByStatus(status);
    }

    public Ticket updateTicket(Long id, Ticket updatedTicketDetails) {
        return ticketRepository.findById(id).map(existingTicket -> {
            existingTicket.setTitle(updatedTicketDetails.getTitle());
            existingTicket.setDescription(updatedTicketDetails.getDescription());
            existingTicket.setStatus(updatedTicketDetails.getStatus());
            return ticketRepository.save(existingTicket);
        }).orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id)); // 👈 Changed here
    }

    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ticket not found with id: " + id); // 👈 Changed here
        }
        ticketRepository.deleteById(id);
    }
}