package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.Comment;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.CommentRepository;
import com.enterprise.ticketmaster.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;

    public CommentController(CommentRepository commentRepository, TicketService ticketService) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<Comment> addComment(@PathVariable Long ticketId, @RequestBody Comment comment, Authentication authentication) {
        // Reuses the same 404 handling as every other ticket lookup in the app
        Ticket ticket = ticketService.getTicketById(ticketId);
        comment.setId(null); // never trust a client-supplied id
        comment.setTicket(ticket);
        comment.setAuthorUsername(authentication != null ? authentication.getName() : "unknown");
        Comment saved = commentRepository.save(comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}