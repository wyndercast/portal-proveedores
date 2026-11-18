package com.autoeastern.backend.shared.integration.valuekeep.client;

import java.util.concurrent.CompletableFuture;

public interface ValuekeepClient {
    <T> CompletableFuture<T> get(String resourcePath, Class<T> responseType);

    <T> CompletableFuture<T> post(String resourcePath, Object requestBody, Class<T> responseType);

    default <T> CompletableFuture<T> put(String resourcePath, Object requestBody, Class<T> responseType) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("La actualización PUT no está disponible."));
    }

    default CompletableFuture<Void> uploadWorkOrderDocument(String workOrderId, String fileName, byte[] pdf) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("La carga de documentos no está disponible."));
    }
}
