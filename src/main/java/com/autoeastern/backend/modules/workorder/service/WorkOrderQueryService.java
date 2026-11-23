package com.autoeastern.backend.modules.workorder.service;

import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.service.ProviderAuthorizationService;
import com.autoeastern.backend.modules.workorder.dto.WorkOrderResponse;
import com.autoeastern.backend.modules.workorder.dto.WorkOrderAttachmentResponse;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepVerificationTypesResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepSchemaEntityStatesResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepWorkshopResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepOperatingStateResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepEntityStateTransitionHistoryResource;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
                    if (!allowedAccessStates().contains(currentState(resource))
                            && !isClosingState(currentState(resource))) {
                        throw new WorkOrderAccessStateException("La OT ya no está disponible para este portal en su estado actual.");
                    }
                    return enrich(resource, session);
                });
    }

    private List<String> allowedAccessStates() {
        return Arrays.stream(valuekeepProperties.getAllowedAccessStates().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private boolean isClosingState(String state) {
        String closingState = valuekeepProperties.getClosingStateKey();
        return closingState != null && !closingState.isBlank() && closingState.equals(state);
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
                    .thenCompose(workshopDescription -> loadOperatingStateDescription(resource)
                            .thenCompose(operatingStateDescription -> loadWorkOrderDates(resource, session.getWorkOrderNumber())
                                    .thenCompose(dates -> loadAttachments(resource.id())
                                            .thenApply(attachments -> map(resource, plans, Map.of(), stateDescription(resource, states), workshopDescription, operatingStateDescription, dates, attachments, session))))));
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
                                .thenCompose(workshopDescription -> loadOperatingStateDescription(resource)
                                        .thenCompose(operatingStateDescription -> loadWorkOrderDates(resource, session.getWorkOrderNumber())
                                                .thenCompose(dates -> loadAttachments(resource.id())
                                                        .thenApply(attachments -> map(resource, plans, options, stateDescription(resource, states), workshopDescription, operatingStateDescription, dates, attachments, session)))))));
    }

    private CompletableFuture<String> loadOperatingStateDescription(ValuekeepWorkOrderResource resource) {
        String operatingStateKey = resource.operatingState();
        String fallback = resource.operatingStateDescription();
        if (operatingStateKey == null || operatingStateKey.isBlank()) return CompletableFuture.completedFuture(fallback);
        try {
            return valuekeepClient.get("maintenanceCore/operatingStates/" + operatingStateKey, ValuekeepOperatingStateResource.class)
                    .handle((operatingState, error) -> error == null && operatingState != null
                            && operatingState.description() != null && !operatingState.description().isBlank()
                            ? operatingState.description() : fallback);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(fallback);
        }
    }

    private CompletableFuture<List<WorkOrderAttachmentResponse>> loadAttachments(String workOrderId) {
        if (workOrderId == null || workOrderId.isBlank()) return CompletableFuture.completedFuture(List.of());
        return valuekeepClient.getWorkOrderAttachments(workOrderId)
                .handle((attachments, error) -> error != null || attachments == null ? List.of() : attachments.stream()
                        .filter(attachment -> attachment != null && attachment.index() != null && attachment.index() >= 0
                                && isImage(attachment.mediaType()) && attachment.name() != null && !attachment.name().isBlank())
                        .map(attachment -> new WorkOrderAttachmentResponse(
                                attachment.name(), attachment.index(), attachment.size() == null ? 0L : Math.round(attachment.size()),
                                attachment.mediaType(), "/api/v1/provider/work-order/attachments/" + attachment.index()))
                        .toList());
    }

    private CompletableFuture<WorkOrderDates> loadWorkOrderDates(ValuekeepWorkOrderResource resource, String workOrderKey) {
        String fallbackStart = null;
        String currentDate = OffsetDateTime.now(ZoneOffset.UTC).toString();
        if (workOrderKey == null || workOrderKey.isBlank()) return CompletableFuture.completedFuture(new WorkOrderDates(fallbackStart, currentDate));
        try {
            return valuekeepClient.getStateTransitionHistories(workOrderKey)
                    .handle((transitions, error) -> {
                        String start = error == null && transitions != null
                                ? transitions.stream()
                                .filter(transition -> isOt04Transition(transition, workOrderKey))
                                .map(ValuekeepEntityStateTransitionHistoryResource.Item::dateTransition)
                                .filter(value -> value != null && !value.isBlank())
                                .findFirst().orElse(fallbackStart)
                                : fallbackStart;
                        return new WorkOrderDates(start, currentDate);
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(new WorkOrderDates(fallbackStart, currentDate));
        }
    }

    private boolean isOt04Transition(ValuekeepEntityStateTransitionHistoryResource.Item transition, String workOrderKey) {
        return transition != null
                && "OT03".equalsIgnoreCase(normalize(transition.newState()))
                && "WORKORDERS.MAINTENANCE".equalsIgnoreCase(normalize(transition.schemaEntity()))
                && normalize(workOrderKey).equals(normalize(transition.entityNaturalKey()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isImage(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) return false;
        String normalized = mediaType.trim().toLowerCase();
        return normalized.startsWith("image/")
                || normalized.equals("application/png")
                || normalized.equals("application/jpeg")
                || normalized.equals("application/jpg")
                || normalized.equals("application/webp");
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
            String operatingStateDescription,
            WorkOrderDates dates,
            List<WorkOrderAttachmentResponse> attachments,
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
                resource.id(), session.getWorkOrderNumber(), firstNonBlank(resource.description(), resource.Description()), resource.remarks(),
                stateKey, stateDescription, resource.asset() != null ? resource.asset() : resource.assetType(),
                assetLabel(resource.asset() != null ? resource.asset() : resource.assetType()), assetCode(resource), assetDescription(resource), resource.workshop(), workshopDescription,
                resource.operatingState(), operatingStateDescription, resource.expectedDate(), resource.expectedEndDate(),
                dates.start(), dates.end(), closed, mappedPlans,
                attachments, new WorkOrderResponse.Summary(mappedPlans.size(), taskCount));
    }

    private record WorkOrderDates(String start, String end) { }

    public CompletableFuture<ValuekeepClient.AttachmentContent> downloadAttachment(Authentication authentication, int index) {
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        return valuekeepClient.getWorkOrderAttachments(session.getWorkOrderId()).thenCompose(attachments -> attachments.stream()
                .filter(attachment -> attachment != null && attachment.index() != null && attachment.index() == index
                        && isImage(attachment.mediaType()) && attachment.name() != null && !attachment.name().isBlank())
                .findFirst()
                .map(attachment -> valuekeepClient.downloadWorkOrderAttachment(session.getWorkOrderId(), attachment.name(), index))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "La fotografía no existe.")));
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
