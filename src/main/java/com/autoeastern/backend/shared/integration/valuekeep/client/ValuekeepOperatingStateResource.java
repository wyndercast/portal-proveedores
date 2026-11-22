package com.autoeastern.backend.shared.integration.valuekeep.client;

public record ValuekeepOperatingStateResource(
        String operatingStateKey,
        String description,
        Boolean isDownState,
        String remarks) {
}
