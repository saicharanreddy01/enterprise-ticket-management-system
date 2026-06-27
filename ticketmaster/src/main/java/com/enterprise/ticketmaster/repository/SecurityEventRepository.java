package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
}