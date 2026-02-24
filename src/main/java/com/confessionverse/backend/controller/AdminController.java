package com.confessionverse.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    // Simple endpoint for testing admin access
    @GetMapping("/secure")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> secureAdminAccess() {
        return ResponseEntity.ok("Access granted to ADMIN secure endpoint");
    }

    // You can add other administrative operations here
}

