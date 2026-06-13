package com.enterprise.ticketmaster.config;

import com.enterprise.ticketmaster.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserRepository userRepository;
    private final JwtFilter jwtFilter;

    public SecurityConfig(UserRepository userRepository, JwtFilter jwtFilter) {
        this.userRepository = userRepository;
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                // Switch to stateless — no sessions, no cookies
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token needed
                        .requestMatchers("/css/**", "/js/**", "/login.html", "/index.html").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()

                        // Admin only
                        .requestMatchers("/api/auth/register").hasRole("ADMIN")
                        .requestMatchers("/api/auth/users").hasRole("ADMIN")
                        .requestMatchers("/api/auth/users/**").hasRole("ADMIN")

                        // Authenticated users
                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/tickets/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/tickets/**").hasRole("ADMIN")
                        .requestMatchers("/api/tickets/**").hasAnyRole("DEVELOPER", "ADMIN")
                        .anyRequest().authenticated()
                )

                // Remove form login and logout — JWT handles auth now
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())

                // Register JWT filter — runs before Spring's auth filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            com.enterprise.ticketmaster.model.User user = userRepository
                    .findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "No user found with username: " + username));

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(user.getRole())
                    .build();
        };
    }

    // Needed by AuthController to verify credentials at login
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Wires UserDetailsService + PasswordEncoder together for authentication
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}