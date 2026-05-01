package com.confessionverse.backend.service;

import com.confessionverse.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestEmailController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/test-email")
    public String testEmail() {

        emailService.sendTestEmail("bejan.vasi@ayhoo.com");

        return "Email sent!";
    }
}