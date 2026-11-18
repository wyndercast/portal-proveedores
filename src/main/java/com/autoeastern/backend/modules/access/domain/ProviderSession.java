package com.autoeastern.backend.modules.access.domain;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProviderSession {
    private final String workOrderId;
    private final String workOrderNumber;
    private final String responsible;
    private final AtomicLong lastActivityEpochSecond;
    private final AtomicLong expiresAtEpochSecond;
    private final Map<String, TaskVerification> taskVerifications = new ConcurrentHashMap<>();

    public ProviderSession(String workOrderId, String workOrderNumber, String responsible, long now, long timeoutSeconds) {
        this.workOrderId = workOrderId;
        this.workOrderNumber = workOrderNumber;
        this.responsible = responsible;
        this.lastActivityEpochSecond = new AtomicLong(now);
        this.expiresAtEpochSecond = new AtomicLong(now + timeoutSeconds);
    }

    public boolean touchIfActive(long now, long timeoutSeconds) {
        while (true) {
            long expiration = expiresAtEpochSecond.get();
            if (expiration <= now) {
                return false;
            }
            if (expiresAtEpochSecond.compareAndSet(expiration, now + timeoutSeconds)) {
                lastActivityEpochSecond.set(now);
                return true;
            }
        }
    }

    public boolean isExpired(long now) { return expiresAtEpochSecond.get() <= now; }
    public String getWorkOrderId() { return workOrderId; }
    public String getWorkOrderNumber() { return workOrderNumber; }
    public String getResponsible() { return responsible; }
    public void saveTaskVerification(String taskInstanceId, String verificationTypeKey, String remarks) {
        taskVerifications.put(taskInstanceId, new TaskVerification(taskInstanceId, verificationTypeKey, remarks));
    }
    public Map<String, TaskVerification> getTaskVerifications() {
        return Collections.unmodifiableMap(taskVerifications);
    }
    public long getLastActivityEpochSecond() { return lastActivityEpochSecond.get(); }
    public long getExpiresAtEpochSecond() { return expiresAtEpochSecond.get(); }

    public record TaskVerification(String taskInstanceId, String verificationTypeKey, String remarks) { }
}
