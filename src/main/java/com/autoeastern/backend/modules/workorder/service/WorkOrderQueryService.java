package com.autoeastern.backend.modules.workorder.service;

import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.service.ProviderAuthorizationService;
import com.autoeastern.backend.modules.workorder.dto.WorkOrderResponse;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepVerificationTypesResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepSchemaEntityStatesResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepWorkshopResource;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkOrderQueryService {
    private static final String VERIFICATION_TYPES_PATH =
            "maintenanceCore/verificationTypes/odata?$filter=Verification eq '%s'&$select=VerificationTypeKey,Description";
    private static final String WORK_ORDER_STATES_PATH =
            "businessCore/vKSchemaEntityStates/odata?$select=StateKey,Description&$filter=SchemaEntity eq 'WORKORDERS.MAINTENANCE'";

    private final ValuekeepClient valuekeepClient;
    private final ProviderAuthorizationService authorizationService;
    private final ValuekeepProperties valuekeepProperties;

    public WorkOrderQueryService(
            ValuekeepClient valuekeepClient,
            ProviderAuthorizationService authorizationService,
            ValuekeepProperties valuekeepProperties) {
        this.valuekeepClient = valuekeepClient;
        this.authorizationService = authorizationService;
        this.valuekeepProperties = valuekeepProperties;
    }

    public CompletableFuture<WorkOrderResponse> getAuthorizedWorkOrder(Authentication authentication) {
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        String path = "maintenance/workOrders/"
                + valuekeepProperties.getCompanyKey() + "/" + session.getWorkOrderNumber();
        return valuekeepClient.get(path, ValuekeepWorkOrderResource.class)
                .thenCompose(resource -> {
                    if (!allowedAccessStates().contains(currentState(resource))) {
                        throw new WorkOrderAccessStateException("La OT ya no está disponible para este portal en su estado actual.");
                    }
                    return enrich(resource, session);
                });
    }

    private List<String> allowedAccessStates() {
        return Arrays.stream(valuekeepProperties.getAllowedAccessStates().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private String currentState(ValuekeepWorkOrderResource resource) {
        return resource.schemaEntityStateKey() != null ? resource.schemaEntityStateKey()
                : resource.state() != null ? resource.state() : resource.schemaEntityState();
    }

    private CompletableFuture<WorkOrderResponse> enrich(
            ValuekeepWorkOrderResource resource,
            ProviderSession session) {
        List<ValuekeepWorkOrderResource.MaintenancePlanResource> plans =
                safeList(resource.workOrderMaintenancePlans());
        List<String> verificationCodes = plans.stream()
                .flatMap(plan -> safeList(plan.workOrderPlanTasks()).stream())
                .map(ValuekeepWorkOrderResource.PlanTaskResource::verification)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();

        if (verificationCodes.isEmpty()) {
            return loadStateDescriptions().thenCompose(states -> loadWorkshopDescription(resource)
                    .thenApply(workshopDescription -> map(resource, plans, Map.of(), stateDescription(resource, states), workshopDescription, session)));
        }

        Map<String, CompletableFuture<List<WorkOrderResponse.VerificationOptionResponse>>> futures =
                verificationCodes.stream().collect(Collectors.toMap(
                        Function.identity(), this::loadVerificationOptions,
                        (first, ignored) -> first, LinkedHashMap::new));
        return CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().join(),
                        (first, ignoredValue) -> first,
                        LinkedHashMap::new)))
                .thenCompose(options -> loadStateDescriptions()
                        .thenCompose(states -> loadWorkshopDescription(resource)
                                .thenApply(workshopDescription -> map(resource, plans, options, stateDescription(resource, states), workshopDescription, session))));
    }

    private CompletableFuture<Map<String, String>> loadStateDescriptions() {
        return valuekeepClient.get(WORK_ORDER_STATES_PATH, ValuekeepSchemaEntityStatesResource.class)
                .handle((states, error) -> {
                    if (error != null || states == null || states.items() == null) return Map.of();
                    return states.items().stream()
                            .filter(item -> item.stateKey() != null && item.description() != null)
                            .collect(Collectors.toMap(ValuekeepSchemaEntityStatesResource.Item::stateKey,
                                    ValuekeepSchemaEntityStatesResource.Item::description,
                                    (first, ignored) -> first, LinkedHashMap::new));
                });
    }

    private String stateDescription(ValuekeepWorkOrderResource resource, Map<String, String> states) {
        String[] candidates = {resource.schemaEntityStateKey(), resource.state(), resource.schemaEntityState()};
        for (String candidate : candidates) {
            if (candidate != null && states.containsKey(candidate)) return states.get(candidate);
        }
        return null;
    }

    private CompletableFuture<String> loadWorkshopDescription(ValuekeepWorkOrderResource resource) {
        if (resource.workshop() == null || resource.workshop().isBlank()) return CompletableFuture.completedFuture(null);
        return valuekeepClient.get("workShopsCore/workshops/" + resource.workshop(), ValuekeepWorkshopResource.class)
                .handle((workshop, error) -> error == null && workshop != null ? workshop.resolvedDescription() : null);
    }

    private CompletableFuture<List<WorkOrderResponse.VerificationOptionResponse>> loadVerificationOptions(
            String verification) {
        String safeVerification = verification.replace("'", "''");
        String path = VERIFICATION_TYPES_PATH.formatted(safeVerification);
        return valuekeepClient.get(path, ValuekeepVerificationTypesResource.class)
                .handle((response, error) -> {
                    if (error != null || response == null) {
                        return List.of();
                    }
                    return safeList(response.items()).stream()
                            .map(item -> new WorkOrderResponse.VerificationOptionResponse(
                                    item.verificationTypeKey(), item.description()))
                            .toList();
                });
    }

    private WorkOrderResponse map(
            ValuekeepWorkOrderResource resource,
            List<ValuekeepWorkOrderResource.MaintenancePlanResource> plans,
            Map<String, List<WorkOrderResponse.VerificationOptionResponse>> options,
            String stateDescription,
            String workshopDescription,
            ProviderSession session) {
        List<WorkOrderResponse.MaintenancePlanResponse> mappedPlans = plans.stream()
                .map(plan -> new WorkOrderResponse.MaintenancePlanResponse(
                        plan.id(),
                        plan.maintenancePlan(),
                        plan.maintenancePlanDescription(),
                        safeList(plan.workOrderPlanTasks()).stream()
                                .sorted(Comparator.comparing(
                                        ValuekeepWorkOrderResource.PlanTaskResource::index,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(task -> new WorkOrderResponse.TaskResponse(
                                        task.id(), task.taskId(), task.code() != null ? task.code() : task.task(),
                                        task.description(), task.remarks(), task.index(), task.verification(),
                                        options.getOrDefault(task.verification(), List.of())))
                                .toList()))
                .toList();
        int taskCount = mappedPlans.stream().mapToInt(plan -> plan.tasks().size()).sum();
        String stateKey = firstNonBlank(resource.schemaEntityStateKey(),
                firstNonBlank(resource.state(), resource.schemaEntityState()));
        boolean closed = !valuekeepProperties.getClosingStateKey().isBlank()
                && valuekeepProperties.getClosingStateKey().equals(stateKey);
        return new WorkOrderResponse(
                resource.id(), session.getWorkOrderNumber(), firstNonBlank(resource.description(), resource.Description()),
                stateKey, stateDescription, resource.asset() != null ? resource.asset() : resource.assetType(),
                assetLabel(resource.asset() != null ? resource.asset() : resource.assetType()), assetCode(resource), assetDescription(resource), resource.workshop(), workshopDescription,
                resource.operatingState(), resource.operatingStateDescription(), resource.expectedDate(), resource.expectedEndDate(), closed, mappedPlans,
                new WorkOrderResponse.Summary(mappedPlans.size(), taskCount));
    }

    private String assetCode(ValuekeepWorkOrderResource resource) {
        Integer asset = resource.asset() != null ? resource.asset() : resource.assetType();
        return switch (asset == null ? 0 : asset) {
            case 1 -> resource.location();
            case 2 -> resource.equipment();
            case 3 -> resource.tool();
            default -> null;
        };
    }

    private String assetDescription(ValuekeepWorkOrderResource resource) {
        Integer asset = resource.asset() != null ? resource.asset() : resource.assetType();
        return switch (asset == null ? 0 : asset) {
            case 1 -> firstNonBlank(resource.locationDescription(), resource.LocationDescription());
            case 2 -> firstNonBlank(resource.equipmentDescription(), resource.EquipmentDescription());
            case 3 -> firstNonBlank(resource.toolDescription(), resource.ToolDescription());
            default -> null;
        };
    }

    private String assetLabel(Integer asset) {
        return switch (asset == null ? 0 : asset) {
            case 1 -> "Localización";
            case 2 -> "Equipamiento";
            case 3 -> "Herramienta";
            default -> "Tipo de activo no disponible";
        };
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
