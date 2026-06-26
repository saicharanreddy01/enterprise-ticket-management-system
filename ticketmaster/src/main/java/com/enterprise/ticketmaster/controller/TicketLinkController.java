package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.model.TicketLink;
import com.enterprise.ticketmaster.repository.TicketLinkRepository;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/tickets/{ticketId}/links")
public class TicketLinkController {

    private final TicketLinkRepository ticketLinkRepository;
    private final TicketRepository ticketRepository;

    public TicketLinkController(TicketLinkRepository ticketLinkRepository,
                                TicketRepository ticketRepository) {
        this.ticketLinkRepository = ticketLinkRepository;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getLinks(@PathVariable Long ticketId) {
        List<TicketLink> links = ticketLinkRepository.findAllLinksForTicket(ticketId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (TicketLink link : links) {
            // Determine which end of the link is the "other" ticket
            Long otherId = link.getSourceTicketId().equals(ticketId)
                    ? link.getTargetTicketId() : link.getSourceTicketId();

            Optional<Ticket> other = ticketRepository.findById(otherId);
            if (other.isEmpty()) continue;

            // Compute readable label from the perspective of the current ticket
            String label = computeLabel(link, ticketId);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("linkId",       link.getId());
            entry.put("linkType",     link.getLinkType());
            entry.put("label",        label);
            entry.put("otherTicketId",   otherId);
            entry.put("otherTicketTitle", other.get().getTitle());
            entry.put("otherStatus",      other.get().getStatus());
            result.add(entry);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createLink(@PathVariable Long ticketId,
                                        @RequestBody Map<String, String> body,
                                        Authentication authentication) {
        Long targetId;
        try {
            targetId = Long.parseLong(body.get("targetTicketId"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid target ticket ID."));
        }

        if (ticketId.equals(targetId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "A ticket cannot link to itself."));
        }
        if (!ticketRepository.existsById(targetId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Target ticket #" + targetId + " does not exist."));
        }
        if (ticketLinkRepository.linkExists(ticketId, targetId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "A link between these tickets already exists."));
        }

        TicketLink.LinkType linkType;
        try {
            linkType = TicketLink.LinkType.valueOf(body.get("linkType"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid link type. Use BLOCKS, RELATED_TO, or DUPLICATES."));
        }

        TicketLink link = new TicketLink();
        link.setSourceTicketId(ticketId);
        link.setTargetTicketId(targetId);
        link.setLinkType(linkType);
        link.setCreatedBy(authentication != null ? authentication.getName() : "system");
        link.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.ok(ticketLinkRepository.save(link));
    }

    @DeleteMapping("/{linkId}")
    public ResponseEntity<?> deleteLink(@PathVariable Long ticketId,
                                        @PathVariable Long linkId) {
        return ticketLinkRepository.findById(linkId)
                .map(link -> {
                    ticketLinkRepository.delete(link);
                    return ResponseEntity.ok(Map.of("message", "Link removed."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String computeLabel(TicketLink link, Long viewingTicketId) {
        boolean isSource = link.getSourceTicketId().equals(viewingTicketId);
        return switch (link.getLinkType()) {
            case BLOCKS      -> isSource ? "blocks"      : "is blocked by";
            case RELATED_TO  -> "is related to";
            case DUPLICATES  -> isSource ? "duplicates"  : "is duplicated by";
        };
    }
}