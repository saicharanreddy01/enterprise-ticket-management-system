package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring generates the SQL automatically:
    // SELECT * FROM users WHERE username = ?
    Optional<User> findByUsername(String username);
}