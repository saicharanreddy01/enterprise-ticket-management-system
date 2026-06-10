package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.User;
import com.enterprise.ticketmaster.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {

        // Check if username already exists
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Username already taken. Please choose a different one.");
        }

        // Validate role — only DEVELOPER or ADMIN allowed
        if (!user.getRole().equals("DEVELOPER") && !user.getRole().equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid role. Must be DEVELOPER or ADMIN.");
        }

        // Hash the password before saving — NEVER store plain text
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("User registered successfully.");
    }
}