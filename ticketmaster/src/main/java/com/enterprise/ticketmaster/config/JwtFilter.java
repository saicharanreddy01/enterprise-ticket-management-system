package com.enterprise.ticketmaster.config;

import com.enterprise.ticketmaster.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Read the Authorization header
        final String authHeader = request.getHeader("Authorization");

        // 2. If no token present, skip — SecurityConfig will handle access denial
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extract the token (remove "Bearer " prefix)
        final String token = authHeader.substring(7);

        try {
            // 4. Extract username from token
            final String username = jwtUtil.extractUsername(token);

            // 5. Only authenticate if not already authenticated
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 6. Load user from database
                UserDetails userDetails = userRepository.findByUsername(username)
                        .map(user -> org.springframework.security.core.userdetails.User
                                .withUsername(user.getUsername())
                                .password(user.getPassword())
                                .roles(user.getRole())
                                .build())
                        .orElse(null);

                // 7. Validate token and set authentication in security context
                if (userDetails != null && jwtUtil.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid token — just continue without authentication
            // SecurityConfig will reject the request if the endpoint requires auth
        }

        // 8. Continue to next filter in chain
        filterChain.doFilter(request, response);
    }
}