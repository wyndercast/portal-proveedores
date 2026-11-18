package com.autoeastern.backend.modules.access.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "provider-access")
public class ProviderAccessProperties {
    @Min(1)
    private long inactivityTimeoutSeconds = 1800;
    @Min(1)
    private int maxAttemptsPerIp = 10;
    @Min(1)
    private int maxAttemptsPerOt = 5;
    @Min(1)
    private long rateWindowSeconds = 300;
    @Min(1)
    private int maxSessions = 10_000;

    public long getInactivityTimeoutSeconds() { return inactivityTimeoutSeconds; }
    public void setInactivityTimeoutSeconds(long value) { this.inactivityTimeoutSeconds = value; }
    public int getMaxAttemptsPerIp() { return maxAttemptsPerIp; }
    public void setMaxAttemptsPerIp(int value) { this.maxAttemptsPerIp = value; }
    public int getMaxAttemptsPerOt() { return maxAttemptsPerOt; }
    public void setMaxAttemptsPerOt(int value) { this.maxAttemptsPerOt = value; }
    public long getRateWindowSeconds() { return rateWindowSeconds; }
    public void setRateWindowSeconds(long value) { this.rateWindowSeconds = value; }
    public int getMaxSessions() { return maxSessions; }
    public void setMaxSessions(int value) { this.maxSessions = value; }
}
