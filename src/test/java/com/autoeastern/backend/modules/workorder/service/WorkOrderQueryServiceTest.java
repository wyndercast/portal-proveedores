package com.autoeastern.backend.modules.workorder.service;

import com.autoeastern.backend.modules.access.domain.ProviderAuthentication;
import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.service.ProviderAuthorizationService;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepVerificationTypesResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepSchemaEntityStatesResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepWorkshopResource;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderQueryServiceTest {

    @Test
    void groupsVerificationCatalogRequestsAndMapsOnlyPlanTasks() {
        AtomicInteger verificationCalls = new AtomicInteger();
        ValuekeepClient client = new ValuekeepClient() {
            @Override
            public <T> CompletableFuture<T> get(String path, Class<T> type) {
                if (path.startsWith("businessCore/vKSchemaEntityStates/odata")) {
                    return CompletableFuture.completedFuture(type.cast(new ValuekeepSchemaEntityStatesResource(
                            List.of(new ValuekeepSchemaEntityStatesResource.Item("OT_STATE_KEY", "Asignado Proveedor", "WORKORDER.MAINTENANCE")), null, null)));
                }
                if (path.startsWith("workShopsCore/workshops/")) {
                    return CompletableFuture.completedFuture(type.cast(new ValuekeepWorkshopResource("Taller Central", "Taller Central", null)));
                }
                if (path.startsWith("maintenanceCore/verificationTypes/odata")) {
                    verificationCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(type.cast(new ValuekeepVerificationTypesResource(
                            List.of(new ValuekeepVerificationTypesResource.Item("SI", "Sí")), null, null)));
                }
                return CompletableFuture.completedFuture(type.cast(workOrder()));
            }

            @Override
            public <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        ValuekeepProperties properties = new ValuekeepProperties();
        properties.setCompanyKey("company");
        properties.setAllowedAccessStates("OT_STATE_KEY");
        WorkOrderQueryService service = new WorkOrderQueryService(
                client, new ProviderAuthorizationService(), properties);

        var result = service.getAuthorizedWorkOrder(new ProviderAuthentication(
                new ProviderSession("id", "0001", "WORKER_01", 100, 1800))).join();

        assertEquals(1, verificationCalls.get());
        assertEquals(1, result.maintenancePlans().size());
        assertEquals(2, result.summary().planTaskCount());
        assertEquals("SI", result.maintenancePlans().get(0).tasks().get(0)
                .verificationOptions().get(0).key());
        assertEquals("EQ-001", result.assetCode());
        assertEquals("Equipment", result.assetDescription());
        assertEquals("Taller Central", result.workshop());
        assertEquals("Taller Central", result.workshopDescription());
        assertEquals("Asignado Proveedor", result.stateDescription());
        assertEquals("Description", result.description());
        assertTrue(result.maintenancePlans().get(0).tasks().stream()
                .allMatch(task -> task.id().startsWith("plan-")));
    }

    private ValuekeepWorkOrderResource workOrder() {
        var first = new ValuekeepWorkOrderResource.PlanTaskResource(
                "plan-task-2", "master-2", "TASK-2", "TASK-2", "Second", null, 2, "VER", null, "plan-1");
        var second = new ValuekeepWorkOrderResource.PlanTaskResource(
                "plan-task-1", "master-1", "TASK-1", "TASK-1", "First", null, 1, "VER", null, "plan-1");
        var plan = new ValuekeepWorkOrderResource.MaintenancePlanResource(
                "plan-1", "PLAN", "master-plan", "Plan", List.of(first, second));
        return new ValuekeepWorkOrderResource(
                "id", "0001", "worker", "OT03", "OT_STATE", "OT_STATE_KEY", "En curso", "Description", null,
                null, 2, null, "EQ-001", null, "Taller Central", null, null, "Equipment", null, null, null, "RUNNING", null,
                null, null, List.of(plan), List.of(new ValuekeepWorkOrderResource.DirectTaskResource(
                        "direct", "master-direct", "DIRECT", "Direct", null, 0)));
    }
}
