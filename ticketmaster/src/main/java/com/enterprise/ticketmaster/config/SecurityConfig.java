package com.enterprise.ticketmaster.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity // Enables Spring Security web support
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 💡 CRITICAL: Ensure static styles and script assets bypass the login wall completely!
                        .requestMatchers("/css/**", "/js/**", "/login.html").permitAll()
                        .requestMatchers("/", "/index.html").authenticated() // Require login for the core dashboard
                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/tickets/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/tickets/**").hasRole("ADMIN")
                        .requestMatchers("/api/tickets/**").hasAnyRole("DEVELOPER", "ADMIN")
                        .anyRequest().authenticated()
                )
                // 💡 SWAP BASIC AUTH FOR CUSTOM FORM AUTH:
                .formLogin(form -> form
                        .loginPage("/login.html")             // URL of our custom login screen
                        .loginProcessingUrl("/perform_login") // POST endpoint Spring monitors automatically
                        .defaultSuccessUrl("/index.html", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/perform_logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login.html?logout=true")
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 4. Set up hardcoded testing users in-memory with clear designated enterprise roles
        UserDetails developer = User.withDefaultPasswordEncoder()
                .username("dev_user")
                .password("dev123")
                .roles("DEVELOPER")
                .build();

        UserDetails admin = User.withDefaultPasswordEncoder()
                .username("admin_user")
                .password("admin123")
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(developer, admin);
    }
}