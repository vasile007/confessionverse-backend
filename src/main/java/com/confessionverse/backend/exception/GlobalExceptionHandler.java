package com.confessionverse.backend.exception;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(Map.of(
                "code", "VALIDATION_ERROR",
                "error", "Validation failed",
                "details", errors
        ));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "NOT_FOUND",
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "NOT_FOUND",
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.NotFoundException.class)
    public ResponseEntity<?> handleNestedNotFound(ResourceNotFoundException.NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "NOT_FOUND",
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Invalid request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", "VALIDATION_ERROR",
                        "error", "Invalid request body"
                ));
    }

    @ExceptionHandler(ReportAlreadyExistsException.class)
    public ResponseEntity<?> handleReportAlreadyExists(ReportAlreadyExistsException ex) {
        log.warn("Report already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        String message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? "Access denied" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", message));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> handleSecurityException(SecurityException ex) {
        log.warn("Security exception: {}", ex.getMessage());
        String message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? "Access denied" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "VALIDATION_ERROR",
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        log.warn("Invalid state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BillingNotConfiguredException.class)
    public ResponseEntity<?> handleBillingNotConfigured(BillingNotConfiguredException ex) {
        log.warn("Billing not configured: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "code", "BILLING_NOT_CONFIGURED",
                        "error", "Stripe is not configured"
                ));
    }

    @ExceptionHandler(FreeLimitReachedException.class)
    public ResponseEntity<?> handleFreeLimitReached(FreeLimitReachedException ex) {
        log.warn("Free plan limit reached: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "code", "FREE_LIMIT_REACHED",
                        "error", "Free plan limit reached. Upgrade to PRO to continue."
                ));
    }

    @ExceptionHandler(PremiumRoomRequiredException.class)
    public ResponseEntity<?> handlePremiumRoomRequired(PremiumRoomRequiredException ex) {
        log.warn("Premium room required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "code", "PREMIUM_ROOM_REQUIRED",
                        "error", "This room is available for PRO members only."
                ));
    }

    @ExceptionHandler(ForbiddenInviteException.class)
    public ResponseEntity<?> handleForbiddenInvite(ForbiddenInviteException ex) {
        log.warn("Forbidden invite: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "code", "FORBIDDEN_INVITE",
                        "error", ex.getMessage()
                ));
    }

    @ExceptionHandler(NotRoomParticipantException.class)
    public ResponseEntity<?> handleNotRoomParticipant(NotRoomParticipantException ex) {
        log.warn("Not room participant: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "code", "NOT_ROOM_PARTICIPANT",
                        "error", ex.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error occurred"));
    }
}

