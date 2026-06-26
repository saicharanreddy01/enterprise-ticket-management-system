package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTicketIdOrderByUploadedAtDesc(Long ticketId);
}