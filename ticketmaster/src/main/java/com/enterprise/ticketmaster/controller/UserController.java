package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final TicketRepository ticketRepository;

    public UserController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(Authentication authentication) {
        Map<String, Object> userDetails = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            userDetails.put("username", authentication.getName());
            userDetails.put("roles", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
        } else {
            userDetails.put("username", "anonymous");
            userDetails.put("roles", java.util.Collections.emptyList());
        }

        return userDetails;
    }

    @GetMapping("/workload")
    public Map<String, Long> getAgentWorkload() {
        List<Object[]> rows = ticketRepository.findOpenTicketCountsByAgent();
        Map<String, Long> workload = new HashMap<>();
        for (Object[] row : rows) {
            workload.put((String) row[0], ((Number) row[1]).longValue());
        }
        return workload;
    }
}