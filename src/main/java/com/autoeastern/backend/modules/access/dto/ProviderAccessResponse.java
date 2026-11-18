package com.autoeastern.backend.modules.access.dto;

public record ProviderAccessResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        WorkOrderReference workOrder) {

    public record WorkOrderReference(String id, String number) { }
}
