package com.autoeastern.backend.shared.integration.valuekeep.exception;

public class ValuekeepRateLimitException extends RuntimeException {
    public ValuekeepRateLimitException(String message) { super(message); }
}
