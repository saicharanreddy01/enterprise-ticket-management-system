package com.enterprise.ticketmaster.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import com.enterprise.ticketmaster.config.JwtUtil;
import com.enterprise.ticketmaster.model.RefreshToken;
import com.enterprise.ticketmaster.model.User;
import com.enterprise.ticketmaster.repository.UserRepository;
import com.enterprise.ticketmaster.repository.RefreshTokenRepository;
import com.enterprise.ticketmaster.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.enterprise.ticketmaster.repository.SecurityEventRepository;
import com.enterprise.ticketmaster.model.SecurityEvent;
import com.enterprise.ticketmaster.model.PasswordResetRequest;
import com.enterprise.ticketmaster.repository.PasswordResetRequestRepository;
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
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityEventRepository securityEventRepository;
    private final PasswordResetRequestRepository passwordResetRequestRepository;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          UserDetailsService userDetailsService,
                          JwtUtil jwtUtil,
                          RefreshTokenService refreshTokenService,
                          RefreshTokenRepository refreshTokenRepository,
                          SecurityEventRepository securityEventRepository,
                          PasswordResetRequestRepository passwordResetRequestRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityEventRepository = securityEventRepository;
        this.passwordResetRequestRepository = passwordResetRequestRepository;
    }

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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        // --- Account lockout check ---
        java.util.Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getLockedUntil() != null
                    && user.getLockedUntil().isAfter(java.time.LocalDateTime.now())) {
                long secondsLeft = java.time.Duration.between(
                        java.time.LocalDateTime.now(), user.getLockedUntil()).getSeconds();
                logSecurityEvent(SecurityEvent.EventType.LOGIN_BLOCKED, username, request,
                        "Account locked. " + secondsLeft + "s remaining.");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Account locked. Try again in " + secondsLeft + " second(s).");
            }
        }
        // --- End lockout check ---

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            userOpt.ifPresent(user -> {
                int attempts = user.getFailedAttempts() + 1;
                user.setFailedAttempts(attempts);
                if (attempts >= 10) {
                    user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
                    user.setFailedAttempts(0);
                    userRepository.save(user);
                } else {
                    userRepository.save(user);
                }
            });
            logSecurityEvent(SecurityEvent.EventType.LOGIN_FAILURE, username, request,
                    "Invalid credentials.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password.");
        }

        // --- Reset failed attempts on successful login ---
        userOpt.ifPresent(user -> {
            if (user.getFailedAttempts() > 0 || user.getLockedUntil() != null) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
        logSecurityEvent(SecurityEvent.EventType.LOGIN_SUCCESS, username, request,
                "Login successful.");
        // --- End reset ---

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String accessToken = jwtUtil.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(username);

        ResponseCookie accessCookie = ResponseCookie.from("jwt_token", accessToken)
                .httpOnly(true)
                .path("/")
                .maxAge(15 * 60)
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("jwt_refresh_token", refreshToken.getToken())
                .httpOnly(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(Map.of(
                "username", username,
                "role", userDetails.getAuthorities().iterator().next()
                        .getAuthority().replace("ROLE_", "")
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // Read refresh token from cookie
        String refreshToken = extractCookie(request, "jwt_refresh_token");
        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }

        // Expire both cookies immediately
        ResponseCookie clearAccess = ResponseCookie.from("jwt_token", "")
                .httpOnly(true).path("/").maxAge(0).sameSite("Strict").build();
        ResponseCookie clearRefresh = ResponseCookie.from("jwt_refresh_token", "")
                .httpOnly(true).path("/").maxAge(0).sameSite("Strict").build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
        logSecurityEvent(SecurityEvent.EventType.LOGOUT,
                request.getParameter("username"), request, "User logged out.");
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String requestRefreshToken = extractCookie(request, "jwt_refresh_token");

        if (requestRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No refresh token cookie found."));
        }

        try {
            return refreshTokenService.findByToken(requestRefreshToken)
                    .map(refreshTokenService::verifyExpiration)
                    .map(RefreshToken::getUser)
                    .map(user -> {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
                        String newAccessToken = jwtUtil.generateToken(userDetails);

                        ResponseCookie accessCookie = ResponseCookie.from("jwt_token", newAccessToken)
                                .httpOnly(true)
                                .path("/")
                                .maxAge(15 * 60)
                                .sameSite("Strict")
                                // .secure(true)  ← uncomment on HTTPS
                                .build();
                        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
                        logSecurityEvent(SecurityEvent.EventType.TOKEN_REFRESHED,
                                user.getUsername(), request, "Access token refreshed.");
                        return ResponseEntity.ok(Map.of("message", "Token refreshed."));
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Refresh token not found in database.")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already taken.");
        }
        if (!user.getRole().equals("DEVELOPER") && !user.getRole().equals("ADMIN") && !user.getRole().equals("USER")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid role. Must be DEVELOPER, ADMIN, or USER.");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email address is required.");
        }
        if (!user.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Please provide a valid email address.");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email address already in use.");
        }

        // --- Password policy enforcement ---
        String password = user.getPassword();
        if (password == null || password.length() < 8) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Password must be at least 8 characters.");
        }
        if (!password.matches(".*[A-Z].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[0-9].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Password must contain at least one number.");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Password must contain at least one special character.");
        }
        // --- End password policy ---

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully.");
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getRole()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/users/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    // Refresh tokens have a FK to users with no cascade — must go first,
                    // otherwise MySQL rejects the user delete with a constraint violation.
                    refreshTokenRepository.deleteByUser(user);
                    userRepository.delete(user);
                    return ResponseEntity.ok("User deleted successfully.");
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User not found with id: " + id));
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void logSecurityEvent(SecurityEvent.EventType type, String username,
                                  HttpServletRequest request, String detail) {
        SecurityEvent event = new SecurityEvent();
        event.setEventType(type);
        event.setUsername(username);
        event.setOccurredAt(java.time.LocalDateTime.now());
        event.setDetail(detail);

        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        event.setIpAddress(ip);
        securityEventRepository.save(event);
    }

    // User submits forgot password request from login page
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required."));
        }
        if (userRepository.findByUsername(username).isEmpty()) {
            // Don't reveal whether username exists — always return success
            return ResponseEntity.ok(Map.of("message", "If this account exists, an admin has been notified."));
        }

        // Check if there's already a pending request for this user
        boolean alreadyPending = passwordResetRequestRepository
                .findTopByUsernameAndStatusOrderByRequestedAtDesc(
                        username, PasswordResetRequest.RequestStatus.PENDING)
                .isPresent();
        if (alreadyPending) {
            return ResponseEntity.ok(Map.of("message", "A request is already pending. Please contact your administrator."));
        }

        PasswordResetRequest req = new PasswordResetRequest();
        req.setUsername(username);
        req.setRequestedAt(java.time.LocalDateTime.now());
        req.setStatus(PasswordResetRequest.RequestStatus.PENDING);
        passwordResetRequestRepository.save(req);

        return ResponseEntity.ok(Map.of("message", "Your request has been submitted. An administrator will reset your password shortly."));
    }

    // Admin views all pending password reset requests
    @GetMapping("/password-requests")
    public ResponseEntity<List<PasswordResetRequest>> getPasswordRequests() {
        return ResponseEntity.ok(passwordResetRequestRepository
                .findByStatusOrderByRequestedAtDesc(PasswordResetRequest.RequestStatus.PENDING));
    }

    // Admin resets a user's password
    @PostMapping("/password-requests/{id}/reset")
    public ResponseEntity<?> resetPasswordByAdmin(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body,
                                                  org.springframework.security.core.Authentication authentication) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters."));
        }

        PasswordResetRequest req = passwordResetRequestRepository.findById(id)
                .orElseThrow(() -> new com.enterprise.ticketmaster.exception.ResourceNotFoundException("Request not found."));

        userRepository.findByUsername(req.getUsername()).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });

        req.setStatus(PasswordResetRequest.RequestStatus.RESOLVED);
        req.setResolvedAt(java.time.LocalDateTime.now());
        req.setResolvedBy(authentication != null ? authentication.getName() : "admin");
        passwordResetRequestRepository.save(req);

        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }

    // Admin dismisses a request without resetting
    @PutMapping("/password-requests/{id}/dismiss")
    public ResponseEntity<?> dismissPasswordRequest(@PathVariable Long id,
                                                    org.springframework.security.core.Authentication authentication) {
        PasswordResetRequest req = passwordResetRequestRepository.findById(id)
                .orElseThrow(() -> new com.enterprise.ticketmaster.exception.ResourceNotFoundException("Request not found."));
        req.setStatus(PasswordResetRequest.RequestStatus.DISMISSED);
        req.setResolvedAt(java.time.LocalDateTime.now());
        req.setResolvedBy(authentication != null ? authentication.getName() : "admin");
        passwordResetRequestRepository.save(req);
        return ResponseEntity.ok(Map.of("message", "Request dismissed."));
    }
}