package com.confessionverse.backend.exception;

public class FreeLimitReachedException extends RuntimeException {
    public FreeLimitReachedException(String message) {
        super(message);
    }
}
