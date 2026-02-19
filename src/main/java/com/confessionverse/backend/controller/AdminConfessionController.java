package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.responseDTO.ConfessionResponseDTO;
import com.confessionverse.backend.service.ConfessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/confessions")
@CrossOrigin(origins = "*")
public class AdminConfessionController {

    private final ConfessionService confessionService;

    public AdminConfessionController(ConfessionService confessionService) {
        this.confessionService = confessionService;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteByAdmin(@PathVariable Long id) {
        confessionService.deleteConfessionByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfessionResponseDTO> hideByAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(confessionService.hideConfessionByAdmin(id));
    }
}
