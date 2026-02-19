package com.confessionverse.backend.exception;

public class PremiumRoomRequiredException extends RuntimeException {
    public PremiumRoomRequiredException(String message) {
        super(message);
    }
}
