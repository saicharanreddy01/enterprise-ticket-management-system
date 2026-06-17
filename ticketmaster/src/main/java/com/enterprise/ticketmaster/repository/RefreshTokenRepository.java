package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.RefreshToken;
import com.enterprise.ticketmaster.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    // Finds a token linked to a specific user (Used for the Upsert pattern)
    Optional<RefreshToken> findByUser(User user);

    // Standard Spring Data derived delete
    void deleteByUser(User user);
}