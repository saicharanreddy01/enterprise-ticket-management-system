package com.enterprise.ticketmaster.exception;

// A dedicated custom exception class for your business logic
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}