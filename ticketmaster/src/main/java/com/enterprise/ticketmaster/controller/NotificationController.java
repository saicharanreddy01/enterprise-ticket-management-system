package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.Notification;
import com.enterprise.ticketmaster.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Notification> getRecentNotifications() {
        return notificationRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<String> markAllRead() {
        List<Notification> unread = notificationRepository.findByReadFalse();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok("All notifications marked as read.");
    }
}