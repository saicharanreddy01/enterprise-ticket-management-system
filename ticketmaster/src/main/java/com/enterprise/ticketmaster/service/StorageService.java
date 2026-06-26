package com.enterprise.ticketmaster.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(MultipartFile file);      // returns stored filename (UUID-based)
    Resource load(String storedFilename);  // returns file as Resource for download
    void delete(String storedFilename);    // removes file from storage
}