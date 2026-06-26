package com.enterprise.ticketmaster.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${app.storage.location}")
    private String storageLocation;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        rootLocation = Paths.get(storageLocation);
        try {
            Files.createDirectories(rootLocation);
            log.info("Storage initialized at: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory.", e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) throw new RuntimeException("Cannot store empty file.");

        String original = file.getOriginalFilename();
        String extension = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf("."))
                : "";
        String storedFilename = UUID.randomUUID() + extension;

        try {
            Path destination = rootLocation.resolve(storedFilename).normalize().toAbsolutePath();
            // Security: prevent path traversal attack
            if (!destination.startsWith(rootLocation.toAbsolutePath())) {
                throw new RuntimeException("Cannot store file outside storage directory.");
            }
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return storedFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + original, e);
        }
    }

    @Override
    public Resource load(String storedFilename) {
        try {
            Path file = rootLocation.resolve(storedFilename).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) return resource;
            throw new RuntimeException("File not found: " + storedFilename);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not read file: " + storedFilename, e);
        }
    }

    @Override
    public void delete(String storedFilename) {
        try {
            Files.deleteIfExists(rootLocation.resolve(storedFilename).normalize());
        } catch (IOException e) {
            log.error("Failed to delete file: {}", storedFilename, e);
        }
    }
}