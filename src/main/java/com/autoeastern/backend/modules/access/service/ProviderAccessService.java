package com.autoeastern.backend.modules.access.service;

import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.dto.ProviderAccessRequest;
import com.autoeastern.backend.modules.access.dto.ProviderAccessResponse;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepClientException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class ProviderAccessService {
    private static final String DENIED_MESSAGE = "No fue posible autorizar el acceso a la orden solicitada.";

    private final ValuekeepClient valuekeepClient;
    private final ProviderSessionStore sessionStore;
    private final ProviderAttemptLimiter attemptLimiter;
    private final ValuekeepProperties valuekeepProperties;

    @Autowired
    public ProviderAccessService(
            ValuekeepClient valuekeepClient,
            ProviderSessionStore sessionStore,
            ProviderAttemptLimiter attemptLimiter,
            ValuekeepProperties valuekeepProperties) {
        this.valuekeepClient = valuekeepClient;
        this.sessionStore = sessionStore;
        this.attemptLimiter = attemptLimiter;
        this.valuekeepProperties = valuekeepProperties;
    }

    public ProviderAccessService(ValuekeepClient valuekeepClient, ProviderSessionStore sessionStore,
                                 ProviderAttemptLimiter attemptLimiter) {
        this(valuekeepClient, sessionStore, attemptLimiter, new ValuekeepProperties());
    }

    public CompletableFuture<ProviderAccessResponse> authorize(
            ProviderAccessRequest request,
            String ipAddress,
            String companyKey) {
        if (!attemptLimiter.isAllowed(ipAddress, request.workOrderNumber())) {
            return failed(new ProviderAccessException(DENIED_MESSAGE));
        }

        String resourcePath = "maintenance/workOrders/"
                + companyKey + "/" + request.workOrderNumber();
        return valuekeepClient.get(resourcePath, ValuekeepWorkOrderResource.class)
                .handle((workOrder, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        if (cause instanceof ValuekeepClientException exception
                                && (exception.getStatusCode() == 400 || exception.getStatusCode() == 404)) {
                            attemptLimiter.recordFailure(ipAddress, request.workOrderNumber());
                            throw new CompletionException(new ProviderAccessException(DENIED_MESSAGE));
                        }
                        throw new CompletionException(cause);
                    }
                    if (workOrder == null
                            || workOrder.id() == null
                            || workOrder.id().isBlank()
                            || workOrder.responsible() == null
                            || !request.workerCode().equals(workOrder.responsible())) {
                        attemptLimiter.recordFailure(ipAddress, request.workOrderNumber());
                        throw new CompletionException(new ProviderAccessException(DENIED_MESSAGE));
                    }
                    String state = currentState(workOrder);
                    if (!allowedStates().contains(state) && !isClosingState(state)) {
                        throw new CompletionException(new ProviderAccessStateException(
                                unavailableStateMessage(workOrder, state)));
                    }

                    ProviderSessionStore.CreatedSession created = sessionStore.create(
                            workOrder.id(), request.workOrderNumber(), workOrder.responsible());
                    return new ProviderAccessResponse(
                            created.token(),
                            "Bearer",
                            created.expiresInSeconds(),
                            new ProviderAccessResponse.WorkOrderReference(
                                    workOrder.id(), request.workOrderNumber()));
                });
    }

    private java.util.List<String> allowedStates() {
        return java.util.Arrays.stream(valuekeepProperties.getAllowedAccessStates().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private boolean isClosingState(String state) {
        String closingState = valuekeepProperties.getClosingStateKey();
        return closingState != null && !closingState.isBlank() && closingState.equals(state);
    }

    private String unavailableStateMessage(ValuekeepWorkOrderResource workOrder, String state) {
        String stateCode = workOrder.schemaEntityStateKey();
        String stateDescription = normalizeRepeatedText(workOrder.schemaEntityStateDescription());
        String stateLabel;
        if (hasText(stateCode) && hasText(stateDescription)) {
            stateLabel = stateCode.trim() + " - " + stateDescription.trim();
        } else if (hasText(stateCode)) {
            stateLabel = stateCode.trim();
        } else if (hasText(stateDescription)) {
            stateLabel = stateDescription.trim();
        } else if (hasText(state)) {
            stateLabel = state.trim();
        } else {
            stateLabel = "sin estado identificado";
        }
        return "La OT está asignada a este proveedor, pero actualmente se encuentra en estado «"
                + stateLabel + "», el cual no está habilitado para su consulta desde el portal.";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeRepeatedText(String value) {
        if (!hasText(value)) {
            return value;
        }
        String text = value.trim();
        int halfLength = text.length() / 2;
        if (text.length() % 2 == 0 && text.substring(0, halfLength).equals(text.substring(halfLength))) {
            return text.substring(0, halfLength);
        }
        return text;
    }

    private String currentState(ValuekeepWorkOrderResource workOrder) {
        return workOrder.schemaEntityStateKey() != null ? workOrder.schemaEntityStateKey()
                : workOrder.state() != null ? workOrder.state() : workOrder.schemaEntityState();
    }

    public void revoke(String rawToken) {
        sessionStore.revoke(rawToken);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        return throwable;
    }

    private <T> CompletableFuture<T> failed(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
