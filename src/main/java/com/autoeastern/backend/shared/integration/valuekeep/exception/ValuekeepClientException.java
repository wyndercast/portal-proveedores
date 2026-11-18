package com.autoeastern.backend.shared.integration.valuekeep.exception;

public class ValuekeepClientException extends RuntimeException {
    private final int statusCode;
    private final Long retryAfterSeconds;

    public ValuekeepClientException(String message, int statusCode) {
        this(message, statusCode, null, null);
    }

    public ValuekeepClientException(String message, int statusCode, Throwable cause) {
        this(message, statusCode, null, cause);
    }

    public ValuekeepClientException(String message, int statusCode, Long retryAfterSeconds) {
        this(message, statusCode, retryAfterSeconds, null);
    }

    public ValuekeepClientException(String message, int statusCode, Long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getStatusCode() { return statusCode; }
    public Long getRetryAfterSeconds() { return retryAfterSeconds; }
}
