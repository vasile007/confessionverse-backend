package com.confessionverse.backend.exception;

public class BillingNotConfiguredException extends RuntimeException {
    public BillingNotConfiguredException(String message) {
        super(message);
    }
}
