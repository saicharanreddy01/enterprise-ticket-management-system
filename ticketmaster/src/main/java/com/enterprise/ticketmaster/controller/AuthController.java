package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.config.JwtUtil;
import com.enterprise.ticketmaster.model.User;
import com.enterprise.ticketmaster.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          UserDetailsService userDetailsService,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    // DTO — only exposes safe fields to the frontend, never the password
    public static class UserResponse {
        public Long id;
        public String username;
        public String role;
        public UserResponse(Long id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }
    }

    // Login endpoint — validates credentials and returns a JWT token
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        try {
            // Verify credentials using Spring's AuthenticationManager
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password.");
        }

        // Credentials valid — generate and return JWT token
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", username,
                "role", userDetails.getAuthorities().iterator().next()
                        .getAuthority().replace("ROLE_", "")
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Username already taken. Please choose a different one.");
        }
        if (!user.getRole().equals("DEVELOPER") && !user.getRole().equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid role. Must be DEVELOPER or ADMIN.");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("User registered successfully.");
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll()
                .stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getRole()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with id: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully.");
    }
}