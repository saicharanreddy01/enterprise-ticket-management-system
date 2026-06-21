package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByOrderByCreatedAtDesc();
    List<Notification> findByReadFalse();
}