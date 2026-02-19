package com.confessionverse.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    // Endpoint simplu pentru testare acces admin
    @GetMapping("/secure")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> secureAdminAccess() {
        return ResponseEntity.ok("Access granted to ADMIN secure endpoint");
    }

    // Poți adăuga și alte operații administrative aici
}

