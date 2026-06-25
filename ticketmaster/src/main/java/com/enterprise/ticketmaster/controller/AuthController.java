package com.enterprise.ticketmaster.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import com.enterprise.ticketmaster.config.JwtUtil;
import com.enterprise.ticketmaster.model.RefreshToken;
import com.enterprise.ticketmaster.model.User;
import com.enterprise.ticketmaster.repository.UserRepository;
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

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          UserDetailsService userDetailsService,
                          JwtUtil jwtUtil,
                          RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
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
                                   HttpServletResponse response) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String accessToken = jwtUtil.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(username);

        // Access token cookie — 15 minutes, HttpOnly, not readable by JavaScript
        ResponseCookie accessCookie = ResponseCookie.from("jwt_token", accessToken)
                .httpOnly(true)
                .path("/")
                .maxAge(15 * 60)
                .sameSite("Strict")
                // .secure(true)  ← uncomment when deploying on HTTPS
                .build();

        // Refresh token cookie — 7 days, HttpOnly
        ResponseCookie refreshCookie = ResponseCookie.from("jwt_refresh_token", refreshToken.getToken())
                .httpOnly(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Strict")
                // .secure(true)  ← uncomment when deploying on HTTPS
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // Return ONLY non-sensitive data in the body — no tokens
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
        if (!user.getRole().equals("DEVELOPER") && !user.getRole().equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid role. Must be DEVELOPER or ADMIN.");
        }
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
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found with id: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully.");
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}