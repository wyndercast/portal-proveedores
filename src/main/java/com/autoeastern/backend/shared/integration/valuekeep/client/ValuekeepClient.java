package com.autoeastern.backend.shared.integration.valuekeep.client;

import java.util.List;
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

    default CompletableFuture<List<ValuekeepWorkOrderAttachment>> getWorkOrderAttachments(String workOrderId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("La consulta de adjuntos no está disponible."));
    }

    default CompletableFuture<AttachmentContent> downloadWorkOrderAttachment(String workOrderId, String fileName, int index) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("La descarga de adjuntos no está disponible."));
    }

    default CompletableFuture<List<ValuekeepStateTransitionResource>> getStateTransitions(String workOrderKey) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("La consulta de transiciones no está disponible."));
    }

    default CompletableFuture<List<ValuekeepEntityStateTransitionHistoryResource.Item>> getStateTransitionHistories(String workOrderKey) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("La consulta del historial de transiciones no está disponible."));
    }

    default CompletableFuture<Void> updateDownAndUpTimeHistoriesFromWorkOrder(String workOrderKey, String totalHours) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("La actualización del tiempo de indisponibilidad no está disponible."));
    }

    record AttachmentContent(byte[] bytes, String mediaType) { }
}
