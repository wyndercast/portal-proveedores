package com.autoeastern.backend.shared.integration.valuekeep.exception;

public class ValuekeepTimeoutException extends ValuekeepClientException {
    public ValuekeepTimeoutException(String message, Throwable cause) {
        super(message, 504, cause);
    }
}
