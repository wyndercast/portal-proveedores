package com.autoeastern.backend.shared.integration.valuekeep.ratelimit;

import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepRateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ValuekeepRateLimiter {
    private final Bucket bucket;
    private final Duration maxWait;
    private final ScheduledExecutorService scheduler;

    public ValuekeepRateLimiter(int requestsPerMinute, long maxWaitSeconds) {
        this.bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
        this.maxWait = Duration.ofSeconds(maxWaitSeconds);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "valuekeep-rate-limiter");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> acquire() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        long deadline = System.nanoTime() + maxWait.toNanos();
        tryAcquire(result, deadline);
        return result;
    }

    private void tryAcquire(CompletableFuture<Void> result, long deadline) {
        if (result.isCancelled()) {
            return;
        }
        if (bucket.tryConsume(1)) {
            result.complete(null);
            return;
        }

        long waitNanos = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
        long remainingNanos = deadline - System.nanoTime();
        if (waitNanos > remainingNanos) {
            result.completeExceptionally(new ValuekeepRateLimitException(
                    "No hay capacidad disponible para Valuekeep dentro del tiempo máximo configurado."));
            return;
        }

        scheduler.schedule(
                () -> tryAcquire(result, deadline),
                Math.max(1, waitNanos),
                TimeUnit.NANOSECONDS);
    }
}
