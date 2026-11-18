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
        String path = "maintenance/workOrders/" + properties.getCompanyKey() + "/" + session.getWorkOrderNumber();
        return valuekeepClient.get(path, ValuekeepWorkOrderResource.class).thenCompose(workOrder -> {
            String current = currentState(workOrder);
            if (!allowed.contains(current) && !properties.getClosingStateKey().equals(current)) throw new FinalizationException(HttpStatus.CONFLICT, "La OT no está en un estado permitido para finalizar.");
            if (properties.getClosingStateKey().equals(current)) return complete(session, rawToken, "CLOSED_CONFIRMED", "ALREADY_CLOSED", current, "La OT ya estaba cerrada.");
            return updateRemarks(session, request.remarks())
                    .thenCompose(ignored -> reportService.upload(authentication, request.previewId()))
                    .thenCompose(upload -> stateTransitionService.transition(session, properties.getClosingStateKey(), "Trabajo ejecutado, verificado y documentado."))
                    .thenCompose(ignored -> confirmState(session, rawToken));
        }).whenComplete((ignored, error) -> operations.remove(session.getWorkOrderNumber()));
    }

    private CompletableFuture<Void> updateRemarks(ProviderSession session, String remarks) {
        String path = "maintenance/workOrders/" + properties.getCompanyKey() + "/"
                + session.getWorkOrderNumber() + "/remarks";
        String value = remarks == null ? "" : remarks.trim();
        return valuekeepClient.put(path, jsonString(value), Void.class);
    }

    private String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                + "\"";
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
