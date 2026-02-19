package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.responseDTO.BillingCheckoutResponseDTO;
import com.confessionverse.backend.dto.responseDTO.BillingSubscriptionSyncDebugResponseDTO;
import com.confessionverse.backend.dto.responseDTO.BillingWebhookConfigDebugResponseDTO;
import com.confessionverse.backend.service.StripeBillingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final StripeBillingService stripeBillingService;
    private final ObjectMapper objectMapper;

    public BillingController(StripeBillingService stripeBillingService, ObjectMapper objectMapper) {
        this.stripeBillingService = stripeBillingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/checkout-session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BillingCheckoutResponseDTO> createCheckoutSession(
            Authentication authentication,
            @RequestParam(name = "currency", required = false) String currency) {
        BillingCheckoutResponseDTO response = stripeBillingService.createCheckoutSessionForUser(authentication.getName(), currency);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(@RequestBody String payload,
                                                             @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        log.info(
                "webhook received payloadLength={} hasStripeSignature={} requestTimestamp={}",
                payload == null ? 0 : payload.length(),
                signature != null && !signature.isBlank(),
                OffsetDateTime.now()
        );
        JsonNode event = parseWebhookEvent(payload);
        String eventId = textValue(event, "id");
        String eventType = textValue(event, "type");
        log.info("Received Stripe webhook request id={} type={}", eventId, eventType);

        if (signature == null || signature.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "error", "Missing Stripe signature"
            ));
        }

        try {
            stripeBillingService.handleWebhook(payload, signature);
        } catch (IllegalArgumentException ex) {
            if ("Invalid Stripe signature".equals(ex.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "code", "VALIDATION_ERROR",
                        "error", "Invalid Stripe signature"
                ));
            }
            throw ex;
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @GetMapping("/debug/webhook-config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BillingWebhookConfigDebugResponseDTO> getWebhookConfigDebug() {
        return ResponseEntity.ok(stripeBillingService.getWebhookConfigDebug());
    }

    @GetMapping("/debug/subscription-sync/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BillingSubscriptionSyncDebugResponseDTO> getSubscriptionSyncDebug(Authentication authentication) {
        BillingSubscriptionSyncDebugResponseDTO response =
                stripeBillingService.getSubscriptionSyncDebugForUser(authentication.getName());
        return ResponseEntity.ok(response);
    }

    private JsonNode parseWebhookEvent(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
