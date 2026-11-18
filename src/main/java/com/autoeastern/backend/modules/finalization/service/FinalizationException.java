package com.autoeastern.backend.modules.finalization.service;

import org.springframework.http.HttpStatus;

public class FinalizationException extends RuntimeException {
    private final HttpStatus status;
    public FinalizationException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
