package com.enterprise.ticketmaster.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WelcomeController {

    @GetMapping("/")
    public String displayWelcomeMessage() {
        return "Welcome to the Enterprise Ticket Management System! Backend is live.";
    }
}
