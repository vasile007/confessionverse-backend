package com.confessionverse.backend.exception;

public class ReportAlreadyExistsException extends RuntimeException {
    public ReportAlreadyExistsException(String message) {
        super(message);
    }
}
