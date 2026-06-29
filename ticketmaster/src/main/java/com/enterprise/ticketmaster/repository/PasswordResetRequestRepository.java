package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.PasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {
    List<PasswordResetRequest> findByStatusOrderByRequestedAtDesc(PasswordResetRequest.RequestStatus status);
    Optional<PasswordResetRequest> findTopByUsernameAndStatusOrderByRequestedAtDesc(
            String username, PasswordResetRequest.RequestStatus status);
}