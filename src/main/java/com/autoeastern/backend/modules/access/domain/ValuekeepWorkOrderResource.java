package com.autoeastern.backend.modules.access.domain;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record ValuekeepWorkOrderResource(
        String id,
        String workOrderKey,
        String responsible,
        String state,
        String schemaEntityState,
        String schemaEntityStateKey,
        String schemaEntityStateDescription,
        String description,
        String Description,
        Integer asset,
        Integer assetType,
        String location,
        @JsonAlias("Equipment") String equipment,
        String tool,
        @JsonAlias("Workshop") String workshop,
        String locationDescription,
        String LocationDescription,
        String equipmentDescription,
        String EquipmentDescription,
        String toolDescription,
        String ToolDescription,
        String operatingState,
        String operatingStateDescription,
        String expectedDate,
        String expectedEndDate,
        List<MaintenancePlanResource> workOrderMaintenancePlans,
        List<DirectTaskResource> workOrderTasks) {

    public ValuekeepWorkOrderResource(String id, String workOrderKey, String responsible) {
        this(id, workOrderKey, responsible, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public ValuekeepWorkOrderResource(String id, String workOrderKey, String responsible, String state) {
        this(id, workOrderKey, responsible, state, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Compatibility constructor for fixtures using the previous canonical resource shape. */
    public ValuekeepWorkOrderResource(
            String id, String workOrderKey, String responsible, String state, String schemaEntityState,
            String schemaEntityStateKey, String description, String Description, Integer asset, Integer assetType,
            String location, String equipment, String tool, String locationDescription,
            String LocationDescription, String equipmentDescription, String EquipmentDescription,
            String toolDescription, String ToolDescription, String operatingState,
            String expectedDate, String expectedEndDate,
            List<MaintenancePlanResource> workOrderMaintenancePlans,
            List<DirectTaskResource> workOrderTasks) {
        this(id, workOrderKey, responsible, state, schemaEntityState, schemaEntityStateKey, null,
                description, Description, asset, assetType, location, equipment, tool, null, locationDescription,
                LocationDescription, equipmentDescription, EquipmentDescription, toolDescription, ToolDescription,
                operatingState, null, expectedDate, expectedEndDate, workOrderMaintenancePlans, workOrderTasks);
    }

    /** Compatibility constructor for the SPEC-002 fixtures that predate asset keys. */
    public ValuekeepWorkOrderResource(
            String id, String workOrderKey, String responsible, String state, String schemaEntityState,
            String description, String Description, Integer asset, Integer assetType,
            String locationDescription, String LocationDescription, String equipmentDescription,
            String EquipmentDescription, String toolDescription, String operatingState,
            String expectedDate, String expectedEndDate,
            List<MaintenancePlanResource> workOrderMaintenancePlans,
            List<DirectTaskResource> workOrderTasks) {
        this(id, workOrderKey, responsible, state, schemaEntityState, null, null, description, Description, asset, assetType,
                null, null, null, null, locationDescription, LocationDescription, equipmentDescription,
                EquipmentDescription, toolDescription, null, operatingState, null, expectedDate, expectedEndDate,
                workOrderMaintenancePlans, workOrderTasks);
    }

    public record MaintenancePlanResource(
            String id,
            String maintenancePlan,
            String maintenancePlanId,
            String maintenancePlanDescription,
            List<PlanTaskResource> workOrderPlanTasks) {
    }

    public record PlanTaskResource(
            String id,
            String taskId,
            String task,
            String code,
            String description,
            String remarks,
            Integer index,
            String verification,
            String verificationId,
            String workOrderMaintenancePlanId) {
    }

    public record DirectTaskResource(
            String id,
            String taskId,
            String task,
            String description,
            String remarks,
            Integer index) {
    }

    public record TaskMatch(MaintenancePlanResource plan, PlanTaskResource task) { }
}
