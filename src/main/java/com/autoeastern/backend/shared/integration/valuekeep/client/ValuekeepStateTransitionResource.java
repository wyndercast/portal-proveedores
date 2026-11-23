package com.autoeastern.backend.shared.integration.valuekeep.client;

public record ValuekeepStateTransitionResource(
        String date,
        String state,
        String stateKey,
        String schemaEntityState,
        String schemaEntityStateKey) {
}
