package com.confessionverse.backend.exception;

public class ForbiddenInviteException extends RuntimeException {
    public ForbiddenInviteException(String message) {
        super(message);
    }
}
