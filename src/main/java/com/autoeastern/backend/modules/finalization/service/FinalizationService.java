package com.autoeastern.backend.modules.finalization.service;

import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.service.ProviderAuthorizationService;
import com.autoeastern.backend.modules.access.service.ProviderSessionStore;
import com.autoeastern.backend.modules.finalization.dto.FinalizationRequest;
import com.autoeastern.backend.modules.finalization.dto.FinalizationResponse;
import com.autoeastern.backend.modules.report.dto.ReportUploadResponse;
import com.autoeastern.backend.modules.report.service.ReportService;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepOperatingStateResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepEntityStateTransitionHistoryResource;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import com.autoeastern.backend.modules.workorder.service.WorkOrderStateTransitionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Service
public class FinalizationService {
    private final ValuekeepClient valuekeepClient;
    private final ProviderAuthorizationService authorizationService;
    private final ProviderSessionStore sessionStore;
    private final ValuekeepProperties properties;
    private final ReportService reportService;
    private final WorkOrderStateTransitionService stateTransitionService;
    private final Map<String, Boolean> operations = new ConcurrentHashMap<>();

    public FinalizationService(ValuekeepClient valuekeepClient, ProviderAuthorizationService authorizationService,
                               ProviderSessionStore sessionStore, ValuekeepProperties properties, ReportService reportService,
                               WorkOrderStateTransitionService stateTransitionService) {
        this.valuekeepClient = valuekeepClient; this.authorizationService = authorizationService; this.sessionStore = sessionStore;
        this.properties = properties; this.reportService = reportService;
        this.stateTransitionService = stateTransitionService;
    }

    @PostConstruct
    void validateClosureConfiguration() {
        if (!properties.isOtClosureEnabled()) return;
        List<String> allowed = Arrays.stream(properties.getAllowedSourceStates().split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
        if (allowed.isEmpty() || properties.getClosingStateKey().isBlank()
                || allowed.contains(properties.getClosingStateKey())) {
            throw new IllegalStateException("La configuración de cierre de OT no es segura.");
        }
    }

    public CompletableFuture<FinalizationResponse> finalize(Authentication authentication, FinalizationRequest request, String rawToken) {
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        if (!request.confirmation()) throw new FinalizationException(HttpStatus.UNPROCESSABLE_ENTITY, "La confirmación del cierre es obligatoria.");
        if (!properties.isOtClosureEnabled()) throw new FinalizationException(HttpStatus.CONFLICT, "El cierre de órdenes todavía no está habilitado.");
        List<String> allowed = Arrays.stream(properties.getAllowedSourceStates().split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
        if (properties.getClosingStateKey().isBlank() || allowed.isEmpty()
                || allowed.contains(properties.getClosingStateKey())) throw new FinalizationException(HttpStatus.SERVICE_UNAVAILABLE, "La configuración de estados de cierre no está completa.");
        if (operations.putIfAbsent(session.getWorkOrderNumber(), Boolean.TRUE) != null) throw new FinalizationException(HttpStatus.CONFLICT, "Ya existe un cierre en curso para esta OT.");
        OffsetDateTime closureRequestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String path = "maintenance/workOrders/" + properties.getCompanyKey() + "/" + session.getWorkOrderNumber();
        return valuekeepClient.get(path, ValuekeepWorkOrderResource.class).thenCompose(workOrder -> {
            String current = currentState(workOrder);
            if (!allowed.contains(current) && !properties.getClosingStateKey().equals(current)) throw new FinalizationException(HttpStatus.CONFLICT, "La OT no está en un estado permitido para finalizar.");
            if (properties.getClosingStateKey().equals(current)) throw new FinalizationException(HttpStatus.CONFLICT, "La OT ya está cerrada y no admite nuevas modificaciones.");
            return reportService.upload(authentication, request.previewId())
                    .thenCompose(upload -> updateDownTimeIfRequired(workOrder, session.getWorkOrderNumber(), closureRequestedAt))
                    .thenCompose(upload -> stateTransitionService.transition(session, properties.getClosingStateKey(), "Trabajo ejecutado, verificado y documentado."))
                    .thenCompose(ignored -> confirmState(session, rawToken));
        }).whenComplete((ignored, error) -> operations.remove(session.getWorkOrderNumber()));
    }

    private CompletableFuture<Void> updateDownTimeIfRequired(
            ValuekeepWorkOrderResource workOrder, String workOrderKey, OffsetDateTime closureRequestedAt) {
        String operatingStateKey = workOrder.operatingState();
        if (operatingStateKey == null || operatingStateKey.isBlank()) return CompletableFuture.completedFuture(null);
        String path = "maintenanceCore/operatingStates/" + operatingStateKey;
        return valuekeepClient.get(path, ValuekeepOperatingStateResource.class)
                .thenCompose(operatingState -> {
                    if (operatingState == null || !Boolean.TRUE.equals(operatingState.isDownState())) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return valuekeepClient.getStateTransitionHistories(workOrderKey)
                            .thenCompose(transitions -> {
                                String startDate = findOt04Date(transitions, workOrderKey);
                                if (startDate == null) {
                                    throw new FinalizationException(HttpStatus.CONFLICT,
                                            "No se encontró la fecha de inicio OT03 para calcular el tiempo de indisponibilidad.");
                                }
                                return valuekeepClient.updateDownAndUpTimeHistoriesFromWorkOrder(
                                        workOrderKey, totalHours(startDate, closureRequestedAt));
                            });
                })
                .exceptionallyCompose(error -> {
                    Throwable cause = unwrap(error);
                    if (cause instanceof FinalizationException finalizationException) {
                        return CompletableFuture.failedFuture(finalizationException);
                    }
                    return CompletableFuture.failedFuture(new FinalizationException(HttpStatus.BAD_GATEWAY,
                            "No fue posible actualizar el tiempo de indisponibilidad del activo."));
                });
    }

    private String findOt04Date(List<ValuekeepEntityStateTransitionHistoryResource.Item> transitions, String workOrderKey) {
        if (transitions == null) return null;
        return transitions.stream()
                .filter(transition -> transition != null
                        && "OT03".equalsIgnoreCase(normalize(transition.newState()))
                        && "WORKORDERS.MAINTENANCE".equalsIgnoreCase(normalize(transition.schemaEntity()))
                        && normalize(workOrderKey).equals(normalize(transition.entityNaturalKey())))
                .map(ValuekeepEntityStateTransitionHistoryResource.Item::dateTransition)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String totalHours(String startDate, OffsetDateTime endDate) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startDate);
            Duration duration = Duration.between(start, endDate);
            if (duration.isNegative()) {
                throw new FinalizationException(HttpStatus.CONFLICT, "La fecha de inicio OT03 no puede ser posterior al cierre.");
            }
            long seconds = duration.getSeconds();
            long days = seconds / 86_400;
            long hours = (seconds % 86_400) / 3_600;
            long minutes = (seconds % 3_600) / 60;
            long remainingSeconds = seconds % 60;
            return days > 0
                    ? "%d.%02d:%02d:%02d".formatted(days, hours, minutes, remainingSeconds)
                    : "%02d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
        } catch (DateTimeParseException exception) {
            throw new FinalizationException(HttpStatus.CONFLICT, "La fecha de inicio OT03 no tiene un formato válido.");
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private CompletableFuture<FinalizationResponse> confirmState(ProviderSession session, String rawToken) {
        String path = "maintenance/workOrders/" + properties.getCompanyKey() + "/" + session.getWorkOrderNumber();
        return valuekeepClient.get(path, ValuekeepWorkOrderResource.class).thenCompose(workOrder -> {
            String state = currentState(workOrder);
            if (!properties.getClosingStateKey().equals(state)) throw new FinalizationException(HttpStatus.CONFLICT, "El informe se adjuntó, pero no se pudo confirmar el estado final de la OT.");
            return complete(session, rawToken, "CLOSED_CONFIRMED", "ATTACHED", state, "La orden de trabajo finalizó correctamente.");
        });
    }

    private CompletableFuture<FinalizationResponse> complete(ProviderSession session, String rawToken, String result, String pdfStatus, String state, String message) {
        sessionStore.revoke(rawToken);
        return CompletableFuture.completedFuture(new FinalizationResponse(result, pdfStatus, "CONFIRMED", state, message));
    }

    private String currentState(ValuekeepWorkOrderResource workOrder) { return workOrder.schemaEntityStateKey() != null ? workOrder.schemaEntityStateKey() : workOrder.state() != null ? workOrder.state() : workOrder.schemaEntityState(); }
}
