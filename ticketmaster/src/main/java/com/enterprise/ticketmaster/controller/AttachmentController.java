package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.Attachment;
import com.enterprise.ticketmaster.repository.AttachmentRepository;
import com.enterprise.ticketmaster.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
public class AttachmentController {

    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;

    public AttachmentController(AttachmentRepository attachmentRepository,
                                StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<List<Attachment>> list(@PathVariable Long ticketId) {
        return ResponseEntity.ok(
                attachmentRepository.findByTicketIdOrderByUploadedAtDesc(ticketId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable Long ticketId,
                                    @RequestParam("file") MultipartFile file,
                                    Authentication authentication) {
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File exceeds 10 MB limit."));
        }

        String storedFilename = storageService.store(file);

        Attachment attachment = new Attachment();
        attachment.setTicketId(ticketId);
        attachment.setOriginalFilename(file.getOriginalFilename());
        attachment.setStoredFilename(storedFilename);
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setUploadedBy(authentication != null ? authentication.getName() : "system");
        attachment.setUploadedAt(LocalDateTime.now());

        return ResponseEntity.ok(attachmentRepository.save(attachment));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long ticketId,
                                             @PathVariable Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found."));

        Resource resource = storageService.load(attachment.getStoredFilename());
        String contentType = attachment.getContentType() != null
                ? attachment.getContentType() : "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<?> delete(@PathVariable Long ticketId,
                                    @PathVariable Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found."));
        storageService.delete(attachment.getStoredFilename());
        attachmentRepository.delete(attachment);
        return ResponseEntity.ok(Map.of("message", "Attachment deleted."));
    }
}