package com.enterprise.ticketmaster.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@org.springframework.stereotype.Controller
public class WelcomeController {

    // Sends anyone hitting the bare domain straight to the marketing landing page
    @GetMapping("/")
    public RedirectView redirectToLanding() {
        return new RedirectView("/landing.html");
    }
}