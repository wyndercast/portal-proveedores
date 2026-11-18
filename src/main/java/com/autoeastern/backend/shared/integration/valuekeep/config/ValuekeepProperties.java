package com.autoeastern.backend.shared.integration.valuekeep.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "valuekeep")
public class ValuekeepProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String tokenUrl;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String tenantKey;

    @NotBlank
    private String orgKey;

    @NotBlank
    private String companyKey;

    @NotBlank
    private String uploadBaseUrl = "https://cloud.valuekeep.com/app";

    private String employeeKey;

    private String allowedAccessStates = "OT03,OT04";
    private String allowedSourceStates = "";
    private String closingStateKey = "";
    private boolean otClosureEnabled = false;
    private boolean closeDowntime = false;
    private boolean applyDowntimeToChildren = false;

    @Min(1)
    private int rateLimit = 30;

    @Min(0)
    private long rateLimitWaitSeconds = 60;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(30);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getTenantKey() { return tenantKey; }
    public void setTenantKey(String tenantKey) { this.tenantKey = tenantKey; }
    public String getOrgKey() { return orgKey; }
    public void setOrgKey(String orgKey) { this.orgKey = orgKey; }
    public String getCompanyKey() { return companyKey; }
    public void setCompanyKey(String companyKey) { this.companyKey = companyKey; }
    public String getUploadBaseUrl() { return uploadBaseUrl; }
    public void setUploadBaseUrl(String uploadBaseUrl) { this.uploadBaseUrl = uploadBaseUrl; }
    public String getEmployeeKey() { return employeeKey; }
    public void setEmployeeKey(String employeeKey) { this.employeeKey = employeeKey; }
    public String getAllowedAccessStates() { return allowedAccessStates; }
    public void setAllowedAccessStates(String allowedAccessStates) { this.allowedAccessStates = allowedAccessStates; }
    public String getAllowedSourceStates() { return allowedSourceStates; }
    public void setAllowedSourceStates(String allowedSourceStates) { this.allowedSourceStates = allowedSourceStates; }
    public String getClosingStateKey() { return closingStateKey; }
    public void setClosingStateKey(String closingStateKey) { this.closingStateKey = closingStateKey; }
    public boolean isOtClosureEnabled() { return otClosureEnabled; }
    public void setOtClosureEnabled(boolean otClosureEnabled) { this.otClosureEnabled = otClosureEnabled; }
    public boolean isCloseDowntime() { return closeDowntime; }
    public void setCloseDowntime(boolean closeDowntime) { this.closeDowntime = closeDowntime; }
    public boolean isApplyDowntimeToChildren() { return applyDowntimeToChildren; }
    public void setApplyDowntimeToChildren(boolean applyDowntimeToChildren) { this.applyDowntimeToChildren = applyDowntimeToChildren; }
    public int getRateLimit() { return rateLimit; }
    public void setRateLimit(int rateLimit) { this.rateLimit = rateLimit; }
    public long getRateLimitWaitSeconds() { return rateLimitWaitSeconds; }
    public void setRateLimitWaitSeconds(long rateLimitWaitSeconds) { this.rateLimitWaitSeconds = rateLimitWaitSeconds; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
