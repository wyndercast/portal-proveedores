package com.autoeastern.backend.modules.access.service;

import com.autoeastern.backend.modules.access.config.ProviderAccessProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProviderAttemptLimiter {
    private final Map<String, Counter> ipCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> otCounters = new ConcurrentHashMap<>();
    private final ProviderAccessProperties properties;

    public ProviderAttemptLimiter(ProviderAccessProperties properties) {
        this.properties = properties;
    }

    public boolean isAllowed(String ipAddress, String workOrderNumber) {
        long now = Instant.now().getEpochSecond();
        return isAllowed(ipCounters, ipAddress, properties.getMaxAttemptsPerIp(), now)
                && isAllowed(otCounters, workOrderNumber, properties.getMaxAttemptsPerOt(), now);
    }

    public void recordFailure(String ipAddress, String workOrderNumber) {
        long now = Instant.now().getEpochSecond();
        record(ipCounters, ipAddress, now);
        record(otCounters, workOrderNumber, now);
    }

    private boolean isAllowed(Map<String, Counter> counters, String key, int limit, long now) {
        Counter counter = counters.get(key);
        return counter == null || counter.isOutsideWindow(now, properties.getRateWindowSeconds())
                || counter.attempts < limit;
    }

    private void record(Map<String, Counter> counters, String key, long now) {
        counters.compute(key, (ignored, counter) -> {
            if (counter == null || counter.isOutsideWindow(now, properties.getRateWindowSeconds())) {
                return new Counter(now, 1);
            }
            counter.attempts++;
            return counter;
        });
    }

    private static final class Counter {
        private final long startedAt;
        private int attempts;

        private Counter(long startedAt, int attempts) {
            this.startedAt = startedAt;
            this.attempts = attempts;
        }

        private boolean isOutsideWindow(long now, long windowSeconds) {
            return now - startedAt >= windowSeconds;
        }
    }
}
