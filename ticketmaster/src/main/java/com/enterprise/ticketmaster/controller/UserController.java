package com.enterprise.ticketmaster.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(Authentication authentication) {
        Map<String, Object> userDetails = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            userDetails.put("username", authentication.getName());
            // Extract roles (e.g., "ROLE_ADMIN", "ROLE_DEVELOPER")
            userDetails.put("roles", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
        } else {
            userDetails.put("username", "anonymous");
            userDetails.put("roles", java.util.Collections.emptyList());
        }

        return userDetails;
    }
}