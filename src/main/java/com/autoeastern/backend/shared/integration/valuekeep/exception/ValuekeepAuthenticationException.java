package com.autoeastern.backend.shared.integration.valuekeep.exception;

public class ValuekeepAuthenticationException extends RuntimeException {
    public ValuekeepAuthenticationException(String message) { super(message); }
    public ValuekeepAuthenticationException(String message, Throwable cause) { super(message, cause); }
}
