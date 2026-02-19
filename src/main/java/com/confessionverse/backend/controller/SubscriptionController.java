package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.SubscriptionRequestDTO;
import com.confessionverse.backend.dto.responseDTO.SubscriptionResponseDTO;
import com.confessionverse.backend.service.SubscriptionService;
import com.confessionverse.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@CrossOrigin(origins = "*")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserService userService;


    public SubscriptionController(SubscriptionService subscriptionService, UserService userService) {
        this.subscriptionService = subscriptionService;
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<SubscriptionResponseDTO>> getAllSubscriptions() {
        return ResponseEntity.ok(subscriptionService.getAll());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<SubscriptionResponseDTO> getMySubscription(Authentication authentication) {
        Long userId = userService.getUserIdByEmail(authentication.getName());
        return ResponseEntity.ok(subscriptionService.getLatestByUserId(userId));
    }

    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.isOwnerBySubscriptionId(#id, authentication.name)")
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponseDTO> getSubscriptionById(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getDtoById(id));
    }


    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionResponseDTO> createSubscription(@RequestBody SubscriptionRequestDTO dto) {
        return ResponseEntity.ok(subscriptionService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionResponseDTO> updateSubscription(
            @PathVariable Long id,
            @RequestBody SubscriptionRequestDTO dto) {
        return ResponseEntity.ok(subscriptionService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSubscription(@PathVariable Long id) {
        subscriptionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}



