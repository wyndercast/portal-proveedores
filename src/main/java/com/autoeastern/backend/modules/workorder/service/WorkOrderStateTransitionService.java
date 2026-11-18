package com.autoeastern.backend.modules.workorder.service;

import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class WorkOrderStateTransitionService {
    private static final DateTimeFormatter VALUEKEEP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    private final ValuekeepClient valuekeepClient;
    private final ValuekeepProperties properties;

    public WorkOrderStateTransitionService(ValuekeepClient valuekeepClient, ValuekeepProperties properties) {
        this.valuekeepClient = valuekeepClient;
        this.properties = properties;
    }

    public CompletableFuture<Void> transition(ProviderSession session, String targetState, String justification) {
        Map<String, Object> body = Map.of(
                "workOrderKey", session.getWorkOrderNumber(),
                "schemaEntityStateKey", targetState,
                "stateChangeDate", OffsetDateTime.now(ZoneOffset.UTC).format(VALUEKEEP_DATE),
                "stateJustificationRemarks", justification,
                "applyDowntimeToChildren", properties.isApplyDowntimeToChildren(),
                "closeDowntime", properties.isCloseDowntime());
        String path = "maintenance/workOrders/changeSchemaEntityState/" + properties.getCompanyKey()
                + "/" + session.getWorkOrderNumber() + "/" + targetState;
        return valuekeepClient.post(path, body, Void.class);
    }

    public CompletableFuture<ValuekeepWorkOrderResource> getCurrent(ProviderSession session) {
        String path = "maintenance/workOrders/" + properties.getCompanyKey() + "/" + session.getWorkOrderNumber();
        return valuekeepClient.get(path, ValuekeepWorkOrderResource.class);
    }

    public String currentState(ValuekeepWorkOrderResource workOrder) {
        return workOrder.schemaEntityStateKey() != null ? workOrder.schemaEntityStateKey()
                : workOrder.state() != null ? workOrder.state() : workOrder.schemaEntityState();
    }
}
