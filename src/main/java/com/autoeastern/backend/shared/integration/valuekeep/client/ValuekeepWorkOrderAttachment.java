package com.autoeastern.backend.shared.integration.valuekeep.client;

public record ValuekeepWorkOrderAttachment(
        String name,
        Integer index,
        Double size,
        String mediaType,
        String streamData) {
}
