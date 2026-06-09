package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.stereotype.Service;
import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // Save a ticket directly into the database table
    public Ticket createTicket(Ticket ticket) {
        return ticketRepository.save(ticket);
    }

    // Fetch all records from the database table
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public List<Ticket> getTicketsByStatus(Status status) {
        return ticketRepository.findByStatus(status);
    }

    public Ticket updateTicket(Long id, Ticket updatedTicketDetails, String resolvedBy) {
        return ticketRepository.findById(id).map(existingTicket -> {
            existingTicket.setTitle(updatedTicketDetails.getTitle());
            existingTicket.setDescription(updatedTicketDetails.getDescription());
            existingTicket.setStatus(updatedTicketDetails.getStatus());
            // Business logic lives here: if resolved, record who signed off
            if (Status.RESOLVED.equals(updatedTicketDetails.getStatus()) && resolvedBy != null) {
                existingTicket.setResolvedBy(resolvedBy);
            }
            return ticketRepository.save(existingTicket);
        }).orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new ResourceNotFoundException("Ticket not found with id: " + id);
        }
        ticketRepository.deleteById(id);
    }
}