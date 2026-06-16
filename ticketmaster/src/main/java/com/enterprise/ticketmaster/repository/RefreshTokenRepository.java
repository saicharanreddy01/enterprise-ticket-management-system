package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.RefreshToken;
import com.enterprise.ticketmaster.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Allows us to find a token object just by providing the raw string
    Optional<RefreshToken> findByToken(String token);

    // Allows us to instantly wipe a user's session from the database (The "Kill Switch")
    void deleteByUser(User user);
}