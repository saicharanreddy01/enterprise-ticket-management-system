package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.repository.TicketRepository;
import com.enterprise.ticketmaster.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public UserController(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
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

    // NOTE: user deletion intentionally lives only in AuthController at
    // DELETE /api/auth/users/{id}, which is locked to ROLE_ADMIN in SecurityConfig.
    // Do not re-add a delete endpoint here — this controller's base path
    // ("/api/users/**") is only behind .authenticated(), not hasRole("ADMIN"),
    // so any logged-in user (including the USER role) would be able to call it.
}