package com.autoeastern.backend.modules.checklist.service;

import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.service.ProviderAuthorizationService;
import com.autoeastern.backend.modules.workorder.service.WorkOrderAccessStateException;
import com.autoeastern.backend.modules.checklist.dto.TaskVerificationBatchRequest;
import com.autoeastern.backend.modules.checklist.dto.TaskVerificationBatchResponse;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepVerificationTypesResource;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class TaskVerificationService {
    private final ValuekeepClient valuekeepClient;
    private final ProviderAuthorizationService authorizationService;
    private final ValuekeepProperties properties;

    public TaskVerificationService(ValuekeepClient valuekeepClient, ProviderAuthorizationService authorizationService, ValuekeepProperties properties) {
        this.valuekeepClient = valuekeepClient; this.authorizationService = authorizationService; this.properties = properties;
    }

    public CompletableFuture<TaskVerificationBatchResponse> saveBatch(Authentication authentication, TaskVerificationBatchRequest request) {
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        String path = "maintenance/workOrders/" + properties.getCompanyKey() + "/" + session.getWorkOrderNumber();
        return valuekeepClient.get(path, ValuekeepWorkOrderResource.class).thenCompose(workOrder -> {
            if (properties.getClosingStateKey().equals(currentState(workOrder))) {
                throw new WorkOrderAccessStateException("La OT está cerrada y no admite nuevas verificaciones.");
            }
            return saveSequentially(session, workOrder, request, 0, new ArrayList<>(), new HashMap<>());
        });
    }

    private String currentState(ValuekeepWorkOrderResource workOrder) {
        return workOrder.schemaEntityStateKey() != null ? workOrder.schemaEntityStateKey()
                : workOrder.state() != null ? workOrder.state() : workOrder.schemaEntityState();
    }

    private CompletableFuture<TaskVerificationBatchResponse> saveSequentially(ProviderSession session, ValuekeepWorkOrderResource workOrder, TaskVerificationBatchRequest request, int index, List<TaskVerificationBatchResponse.Result> results, Map<String, CompletableFuture<Map<String, Boolean>>> catalogs) {
        if (index == request.responses().size()) {
            int saved = (int) results.stream().filter(result -> "SAVED".equals(result.status())).count();
            int failed = (int) results.stream().filter(result -> "FAILED".equals(result.status())).count();
            int uncertain = (int) results.stream().filter(result -> "UNCERTAIN".equals(result.status())).count();
            return CompletableFuture.completedFuture(new TaskVerificationBatchResponse(request.operationId(), new TaskVerificationBatchResponse.Summary(saved, failed, uncertain), results));
        }
        var item = request.responses().get(index);
        var match = findTask(workOrder, item.taskInstanceId());
        if (match == null) {
            results.add(new TaskVerificationBatchResponse.Result(item.taskInstanceId(), "FAILED", "La tarea no pertenece a la OT autorizada."));
            return saveSequentially(session, workOrder, request, index + 1, results, catalogs);
        }
        if (match.task().verification() == null || match.task().verification().isBlank()) {
            results.add(new TaskVerificationBatchResponse.Result(item.taskInstanceId(), "FAILED", "La tarea no admite verificaciones."));
            return saveSequentially(session, workOrder, request, index + 1, results, catalogs);
        }
        String verification = match.task().verification().replace("'", "''");
        CompletableFuture<Map<String, Boolean>> options = catalogs.computeIfAbsent(verification, code -> valuekeepClient.get("maintenanceCore/verificationTypes/odata?$filter=Verification eq '" + code + "'&$select=VerificationTypeKey,Description", ValuekeepVerificationTypesResource.class).thenApply(resource -> resource.items() == null ? Map.of() : resource.items().stream().collect(java.util.stream.Collectors.toMap(ValuekeepVerificationTypesResource.Item::verificationTypeKey, option -> Boolean.TRUE.equals(option.remarksRequired()), (first, ignored) -> first))));
        return options.thenCompose(validOptions -> {
            if (!validOptions.containsKey(item.verificationTypeKey())) {
                results.add(new TaskVerificationBatchResponse.Result(item.taskInstanceId(), "FAILED", "La opción de verificación no es válida para la tarea."));
                return saveSequentially(session, workOrder, request, index + 1, results, catalogs);
            }
            boolean remarksRequired = Boolean.TRUE.equals(validOptions.get(item.verificationTypeKey()));
            if (remarksRequired && (item.remarks() == null || item.remarks().isBlank())) {
                results.add(new TaskVerificationBatchResponse.Result(item.taskInstanceId(), "FAILED", "Esta opción requiere observaciones."));
                return saveSequentially(session, workOrder, request, index + 1, results, catalogs);
            }
            session.saveTaskVerification(item.taskInstanceId(), item.verificationTypeKey(), item.remarks());
            results.add(new TaskVerificationBatchResponse.Result(item.taskInstanceId(), "SAVED", null));
            return saveSequentially(session, workOrder, request, index + 1, results, catalogs);
        });
    }

    private ValuekeepWorkOrderResource.TaskMatch findTask(ValuekeepWorkOrderResource workOrder, String taskId) {
        if (workOrder.workOrderMaintenancePlans() == null) return null;
        return workOrder.workOrderMaintenancePlans().stream().flatMap(plan -> plan.workOrderPlanTasks() == null ? java.util.stream.Stream.empty() : plan.workOrderPlanTasks().stream().map(task -> new ValuekeepWorkOrderResource.TaskMatch(plan, task))).filter(match -> taskId.equals(match.task().id())).findFirst().orElse(null);
    }
}
