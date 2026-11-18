package com.autoeastern.backend.modules.access.service;

import com.autoeastern.backend.modules.access.config.ProviderAccessProperties;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.dto.ProviderAccessRequest;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderAccessServiceTest {

    @Test
    void requiresExactCaseSensitiveResponsibleMatch() {
        ValuekeepClient client = new ValuekeepClient() {
            @Override
            public <T> CompletableFuture<T> get(String path, Class<T> type) {
                return CompletableFuture.completedFuture(type.cast(
                        new ValuekeepWorkOrderResource("id-1", "0000001917", "WORKER_01", "OT03")));
            }

            @Override
            public <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        ProviderAccessService service = new ProviderAccessService(
                client,
                new ProviderSessionStore(new ProviderAccessProperties()),
                new ProviderAttemptLimiter(new ProviderAccessProperties()));

        var response = service.authorize(
                new ProviderAccessRequest("0000001917", "WORKER_01"),
                "127.0.0.1",
                "company")
                .join();

        assertEquals("0000001917", response.workOrder().number());
        assertThrows(Exception.class, () -> service.authorize(
                new ProviderAccessRequest("0000001917", "worker_01"),
                "127.0.0.2",
                "company").join());
    }
}
