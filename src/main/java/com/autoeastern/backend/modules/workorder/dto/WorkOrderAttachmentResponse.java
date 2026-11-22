package com.autoeastern.backend.modules.workorder.dto;

public record WorkOrderAttachmentResponse(
        String name,
        int index,
        long size,
        String mediaType,
        String thumbnailUrl) {
}
