package com.autoeastern.backend.modules.workorder.dto;

import java.util.List;

public record WorkOrderResponse(
        String id,
        String number,
        String description,
        String remarks,
        String state,
        String stateDescription,
        Integer asset,
        String assetTypeLabel,
        String assetCode,
        String assetDescription,
        String workshop,
        String workshopDescription,
        String operatingState,
        String operatingStateDescription,
        String expectedDate,
        String expectedEndDate,
        String startDate,
        String endDate,
        boolean closed,
        List<MaintenancePlanResponse> maintenancePlans,
        List<WorkOrderAttachmentResponse> attachments,
        Summary summary) {

    public record MaintenancePlanResponse(
            String id,
            String code,
            String description,
            List<TaskResponse> tasks) {
    }

    public record TaskResponse(
            String id,
            String taskId,
            String code,
            String description,
            String remarks,
            Integer index,
            String verification,
            List<VerificationOptionResponse> verificationOptions) {
    }

    public record VerificationOptionResponse(String key, String description) {
    }

    public record Summary(int maintenancePlanCount, int planTaskCount) {
    }
}
