package com.autoeastern.backend.shared.integration.valuekeep.ratelimit;

import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepRateLimitException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValuekeepRateLimiterTest {

    @Test
    void allowsConfiguredCapacityAndFailsWithoutWaitingWhenDeadlineIsZero() {
        ValuekeepRateLimiter limiter = new ValuekeepRateLimiter(1, 0);

        assertDoesNotThrow(() -> limiter.acquire().join());

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> limiter.acquire().join());
        assertThrows(ValuekeepRateLimitException.class, () -> { throw exception.getCause(); });
    }
}
