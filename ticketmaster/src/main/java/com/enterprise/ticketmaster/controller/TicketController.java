package com.enterprise.ticketmaster.controller;

import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.data.domain.Page;
import com.enterprise.ticketmaster.model.Status;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.enterprise.ticketmaster.model.Priority;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.service.TicketService;
import org.springframework.web.bind.annotation.*;
import com.enterprise.ticketmaster.model.TicketSuggestRequest;
import com.enterprise.ticketmaster.model.TicketSuggestResponse;
import com.enterprise.ticketmaster.service.ClassifierService;
import java.util.ArrayList;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final ClassifierService classifierService;

    public TicketController(TicketService ticketService, ClassifierService classifierService) {
        this.ticketService = ticketService;
        this.classifierService = classifierService;
    }

    @GetMapping
    public List<Ticket> getAllTickets() {
        return ticketService.getAllTickets();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Ticket>> searchTickets(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ticketService.searchTickets(q, page, size));
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
    public List<Ticket> getTicketsByStatus(@PathVariable Status status) {
        return ticketService.getTicketsByStatus(status);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ticket> updateTicket(@PathVariable Long id, @Valid @RequestBody Ticket updatedTicket, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ticketService.updateTicket(id, updatedTicket, username));
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<Ticket> assignAgent(@PathVariable Long id,
                                              @RequestBody Map<String, String> body,
                                              Authentication authentication) {
        String agent = body.get("assignedAgent");
        String actor = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(ticketService.assignAgent(id, agent, actor));
    }



    @PutMapping("/bulk-update")
    public ResponseEntity<?> bulkUpdate(@RequestBody Map<String, Object> body,
                                        Authentication authentication) {
        // Role check
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        String newStatus        = (String) body.get("status");
        String newAssignedAgent = (String) body.get("assignedAgent");
        String actor = authentication != null ? authentication.getName() : "system";

        // Block developers from using CLOSED or OPEN as bulk status
        if (!isAdmin && newStatus != null &&
                (newStatus.equals("CLOSED") || newStatus.equals("OPEN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Developers cannot bulk-set status to OPEN or CLOSED."));
        }

        // Block developers from bulk-assigning agents
        if (!isAdmin && newAssignedAgent != null && !newAssignedAgent.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only admins can bulk-assign agents."));
        }

        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ticketIds");

        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No ticket IDs provided."));
        }

        List<Ticket> updated = new ArrayList<>();
        for (Integer rawId : ids) {
            Long id = rawId.longValue();
            try {
                Ticket ticket = ticketService.getTicketById(id);
                if (newStatus != null && !newStatus.isBlank()) {
                    ticket = ticketService.updateTicket(id,
                            buildStatusUpdate(ticket, newStatus), actor);
                }
                if (newAssignedAgent != null && !newAssignedAgent.isBlank()) {
                    ticket = ticketService.assignAgent(id, newAssignedAgent, actor);
                }
                updated.add(ticket);
            } catch (Exception e) {
                // Skip tickets that fail — don't abort the whole batch
            }
        }
        return ResponseEntity.ok(Map.of("updated", updated.size()));
    }

    private Ticket buildStatusUpdate(Ticket existing, String status) {
        Ticket t = new Ticket();
        t.setTitle(existing.getTitle());
        t.setDescription(existing.getDescription());
        t.setPriority(existing.getPriority());
        t.setStatus(com.enterprise.ticketmaster.model.Status.valueOf(status));
        return t;
    }

    @DeleteMapping("/{id}")
    public String deleteTicket(@PathVariable Long id) {
        // 💡 Changed lowercase 's' to capital 'S'
        ticketService.deleteTicket(id);
        return "Ticket with ID " + id + " has been successfully deleted from the database.";
    }

    @PostMapping("/suggest")
    public ResponseEntity<TicketSuggestResponse> suggest(@Valid @RequestBody TicketSuggestRequest request) {
        return ResponseEntity.ok(classifierService.classify(request));
    }

    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tickets.csv\"");

        List<Ticket> tickets = ticketService.getAllTickets();

        try (var writer = response.getWriter()) {
            writer.println("ID,Title,Status,Priority,Category,Raised By,Assigned To,Assigned Agent,SLA Breached,Created At,Resolved At");
            for (Ticket t : tickets) {
                writer.println(String.join(",",
                        str(t.getId()),
                        csvEscape(t.getTitle()),
                        str(t.getStatus()),
                        str(t.getPriority()),
                        t.getCategory() != null ? csvEscape(t.getCategory().getName()) : "",
                        csvEscape(t.getRaisedBy()),
                        csvEscape(t.getAssignedTo()),
                        csvEscape(t.getAssignedAgent()),
                        String.valueOf(t.getSlaBreached()),
                        str(t.getCreatedAt()),
                        str(t.getResolvedAt())
                ));
            }
        }
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }
    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
