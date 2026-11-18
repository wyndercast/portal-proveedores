package com.autoeastern.backend.modules.workorder.service;

import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.service.ProviderAuthorizationService;
import com.autoeastern.backend.modules.workorder.dto.WorkOrderStartResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkOrderStartService {
    private final ProviderAuthorizationService authorizationService;
    private final WorkOrderStateTransitionService stateService;
    private final Map<String, Boolean> operations = new ConcurrentHashMap<>();

    public WorkOrderStartService(ProviderAuthorizationService authorizationService,
                                 WorkOrderStateTransitionService stateService) {
        this.authorizationService = authorizationService;
        this.stateService = stateService;
    }

    public CompletableFuture<WorkOrderStartResponse> start(Authentication authentication) {
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        if (operations.putIfAbsent(session.getWorkOrderNumber(), Boolean.TRUE) != null) {
            throw new WorkOrderStartException("Ya existe un inicio en curso para esta OT.");
        }
        return stateService.getCurrent(session).thenCompose(workOrder -> {
            String current = stateService.currentState(workOrder);
            if ("OT04".equals(current)) {
                return CompletableFuture.completedFuture(new WorkOrderStartResponse(current, false));
            }
            if (!"OT03".equals(current)) {
                throw new WorkOrderStartException("La OT no está disponible para iniciar el trabajo.");
            }
            return stateService.transition(session, "OT04", "Inicio del trabajo planificado.")
                    .thenCompose(ignored -> stateService.getCurrent(session))
                    .thenApply(updated -> {
                        String confirmed = stateService.currentState(updated);
                        if (!"OT04".equals(confirmed)) {
                            throw new WorkOrderStartException("No se pudo confirmar el inicio de la OT.");
                        }
                        return new WorkOrderStartResponse(confirmed, true);
                    });
        }).whenComplete((ignored, error) -> operations.remove(session.getWorkOrderNumber()));
    }

    public static class WorkOrderStartException extends RuntimeException {
        public WorkOrderStartException(String message) { super(message); }
    }
}
