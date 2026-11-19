package com.autoeastern.backend.modules.report.service;

import com.autoeastern.backend.modules.access.domain.ProviderSession;
import com.autoeastern.backend.modules.access.domain.ValuekeepWorkOrderResource;
import com.autoeastern.backend.modules.access.service.ProviderAuthorizationService;
import com.autoeastern.backend.modules.workorder.service.WorkOrderAccessStateException;
import com.autoeastern.backend.modules.report.dto.ReportPreviewResponse;
import com.autoeastern.backend.modules.report.dto.ReportUploadResponse;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepClient;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepSchemaEntityStatesResource;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepWorkshopResource;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepClientException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReportService {
    private static final DateTimeFormatter REPORT_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Bogota");
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);
    private static final int MAX_PHOTOS = 6;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_IMAGE_BYTES = 30L * 1024 * 1024;
    private static final long PREVIEW_TTL_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final String WORK_ORDER_STATES_PATH =
            "businessCore/vKSchemaEntityStates/odata?$select=StateKey,Description&$filter=SchemaEntity eq 'WORKORDERS.MAINTENANCE'";

    private final ValuekeepClient valuekeepClient;
    private final ProviderAuthorizationService authorizationService;
    private final ValuekeepProperties properties;
    private final TemplateEngine templateEngine;
    private final Map<String, PreviewArtifact> previews = new ConcurrentHashMap<>();

    public ReportService(ValuekeepClient valuekeepClient, ProviderAuthorizationService authorizationService,
                         ValuekeepProperties properties, TemplateEngine templateEngine) {
        this.valuekeepClient = valuekeepClient; this.authorizationService = authorizationService;
        this.properties = properties; this.templateEngine = templateEngine;
    }

    public CompletableFuture<GeneratedReport> preview(Authentication authentication, String technicianName, String receiverName, String remarks,
                                                       MultipartFile technicianSignature, MultipartFile receiverSignature,
                                                       List<MultipartFile> photos) {
        cleanupExpired();
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        validateName(technicianName, "El nombre del técnico es obligatorio.");
        validateName(receiverName, "El nombre de quien recibe el servicio es obligatorio.");
        validateSignature(technicianSignature, "La firma del técnico");
        validateSignature(receiverSignature, "La firma de quien recibe el servicio");
        List<MultipartFile> safePhotos = photos == null ? List.of() : photos;
        validatePhotos(safePhotos);
        String path = "maintenance/workOrders/" + properties.getCompanyKey() + "/" + session.getWorkOrderNumber();
        return valuekeepClient.get(path, ValuekeepWorkOrderResource.class)
                .thenCompose(workOrder -> {
                    if (properties.getClosingStateKey().equals(currentStateKey(workOrder))) {
                        throw new WorkOrderAccessStateException("La OT está cerrada y no admite nuevos informes.");
                    }
                    return loadStateDescriptions()
                            .thenCompose(states -> loadWorkshopDescription(workOrder)
                                    .thenCompose(workshopDescription -> loadVerifiedTasks(session, workOrder)
                                            .thenApply(model -> {
                                                String reportStateKey = reportStateKey(workOrder);
                                                return new VerifiedModel(model.workOrder(), model.tasks(), reportStateKey,
                                                        stateDescription(reportStateKey, states), workshopDescription);
                                            })));
                })
                .thenApply(model -> {
                    Map<String, Object> view = buildView(model.workOrder(), model.tasks(), model.stateKey(), model.stateDescription(), model.workshopDescription(), remarks, technicianName.trim(), receiverName.trim(), technicianSignature, receiverSignature, safePhotos);
                    byte[] pdf = render(view);
                    String id = UUID.randomUUID().toString();
                    String fileName = safeFileName(session.getWorkOrderNumber());
                    String hash = sha256(pdf);
                    previews.put(id, new PreviewArtifact(model.workOrder().id(), fileName, hash, pdf, System.currentTimeMillis() + PREVIEW_TTL_MILLIS));
                    return new GeneratedReport(pdf, new ReportPreviewResponse(id, fileName, hash));
                });
    }

    private CompletableFuture<Map<String, String>> loadStateDescriptions() {
        return valuekeepClient.get(WORK_ORDER_STATES_PATH, ValuekeepSchemaEntityStatesResource.class)
                .handle((states, error) -> {
                    if (error != null || states == null || states.items() == null) return Map.of();
                    Map<String, String> result = new LinkedHashMap<>();
                    states.items().forEach(item -> {
                        if (item.stateKey() != null && item.description() != null) result.putIfAbsent(item.stateKey(), item.description());
                    });
                    return result;
                });
    }

    private String reportStateKey(ValuekeepWorkOrderResource workOrder) {
        if (properties.getClosingStateKey() != null && !properties.getClosingStateKey().isBlank()) {
            return properties.getClosingStateKey().trim();
        }
        return currentStateKey(workOrder);
    }

    private String currentStateKey(ValuekeepWorkOrderResource workOrder) {
        String[] candidates = {workOrder.schemaEntityStateKey(), workOrder.state(), workOrder.schemaEntityState()};
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate;
        }
        return null;
    }

    private String stateDescription(String stateKey, Map<String, String> states) {
        return stateKey == null ? null : states.get(stateKey);
    }

    private CompletableFuture<String> loadWorkshopDescription(ValuekeepWorkOrderResource workOrder) {
        if (workOrder.workshop() == null || workOrder.workshop().isBlank()) return CompletableFuture.completedFuture(null);
        return valuekeepClient.get("workShopsCore/workshops/" + workOrder.workshop(), ValuekeepWorkshopResource.class)
                .handle((workshop, error) -> error == null && workshop != null ? workshop.resolvedDescription() : null);
    }

    public byte[] previewBytes(String previewId, Authentication authentication) {
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        PreviewArtifact artifact = requirePreview(previewId, session.getWorkOrderId());
        return artifact.pdf();
    }

    public CompletableFuture<ReportUploadResponse> upload(Authentication authentication, String previewId) {
        ProviderSession session = authorizationService.requireAuthenticatedSession(authentication);
        PreviewArtifact artifact = requirePreview(previewId, session.getWorkOrderId());
        synchronized (artifact) {
            if (artifact.uploaded()) {
                return CompletableFuture.completedFuture(new ReportUploadResponse("ALREADY_UPLOADED", artifact.fileName()));
            }
            artifact.markUploaded();
        }
        return valuekeepClient.get("maintenance/workOrders/" + properties.getCompanyKey() + "/" + session.getWorkOrderNumber(), ValuekeepWorkOrderResource.class)
                .thenCompose(workOrder -> {
                    if (properties.getClosingStateKey().equals(currentStateKey(workOrder))) {
                        artifact.markNotUploaded();
                        throw new WorkOrderAccessStateException("La OT está cerrada y no admite nuevos informes.");
                    }
                    return valuekeepClient.uploadWorkOrderDocument(session.getWorkOrderId(), artifact.fileName(), artifact.pdf());
                })
                .handle((ignored, error) -> {
                    if (error != null) {
                        artifact.markNotUploaded();
                        Throwable cause = error instanceof CompletionException completionException && completionException.getCause() != null
                                ? completionException.getCause() : error;
                        if (cause instanceof ValuekeepClientException valuekeepException) {
                            throw valuekeepException;
                        }
                        throw new RuntimeException("No fue posible adjuntar el informe en Valuekeep.", cause);
                    }
                    previews.remove(previewId);
                    return new ReportUploadResponse("UPLOADED", artifact.fileName());
                });
    }

    private CompletableFuture<VerifiedModel> loadVerifiedTasks(ProviderSession session, ValuekeepWorkOrderResource workOrder) {
        LOGGER.info("Preparando informe; usando verificaciones en memoria de la sesión para la OT {}", session.getWorkOrderNumber());
        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Map<String, Object>> taskIndex = new HashMap<>();
        safe(workOrder.workOrderMaintenancePlans()).forEach(plan -> safe(plan.workOrderPlanTasks()).forEach(task -> {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("code", task.code() != null ? task.code() : task.task());
            taskData.put("description", task.description());
            taskData.put("index", task.index());
            taskIndex.put(task.id(), taskData);
        }));
        safe(workOrder.workOrderTasks()).forEach(task -> {
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("code", task.task());
            taskData.put("description", task.description());
            taskData.put("index", task.index());
            taskIndex.put(task.id(), taskData);
        });
        session.getTaskVerifications().values().forEach(verification -> {
            Map<String, Object> task = taskIndex.get(verification.taskInstanceId());
            if (task != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", task.get("code"));
                row.put("description", task.get("description"));
                row.put("response", verification.verificationTypeKey());
                row.put("remarks", verification.remarks());
                row.put("index", task.get("index"));
                tasks.add(row);
            }
        });
        tasks.sort(Comparator.comparing(row -> row.get("index") instanceof Number n ? n.intValue() : Integer.MAX_VALUE));
        if (tasks.isEmpty()) {
            throw new ReportValidationException("La OT no tiene respuestas de tareas guardadas para incluir en el informe.");
        }
        return CompletableFuture.completedFuture(new VerifiedModel(workOrder, tasks, null, null, null));
    }

    private Map<String, Object> buildView(ValuekeepWorkOrderResource workOrder, List<Map<String, Object>> tasks, String stateKey, String stateDescription, String workshopDescription, String generalRemarks,
                                           String technicianName, String receiverName, MultipartFile technicianSignature,
                                           MultipartFile receiverSignature, List<MultipartFile> photos) {
        Map<String, Object> view = new HashMap<>();
        view.put("generatedAt", formatDateTime(OffsetDateTime.now(ZoneOffset.UTC).toString())); view.put("number", workOrder.workOrderKey());
        view.put("description", first(workOrder.description(), workOrder.Description())); view.put("state", stateLabel(stateKey, stateDescription));
        view.put("assetType", assetLabel(workOrder.asset() != null ? workOrder.asset() : workOrder.assetType()));
        view.put("assetCode", assetLabel(workOrder)); view.put("assetDescription", assetDescription(workOrder)); view.put("workshop", workshopLabel(workOrder.workshop(), workshopDescription)); view.put("operatingState", workOrder.operatingState());
        view.put("expectedDateFormatted", formatDateTime(workOrder.expectedDate()));
        view.put("expectedEndDateFormatted", formatDateTime(workOrder.expectedEndDate()));
        view.put("tasks", tasks); view.put("generalRemarks", generalRemarks == null ? "" : generalRemarks.trim());
        view.put("logoData", logoDataUri());
        view.put("technicianName", technicianName); view.put("receiverName", receiverName);
        view.put("technicianSignature", dataUri(technicianSignature, "image/png")); view.put("receiverSignature", dataUri(receiverSignature, "image/png"));
        List<Map<String, Object>> photoRows = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) { Map<String, Object> row = new HashMap<>(); row.put("number", i + 1); row.put("data", dataUri(photos.get(i), photos.get(i).getContentType())); photoRows.add(row); }
        view.put("photos", photoRows); return view;
    }

    private byte[] render(Map<String, Object> view) {
        Context context = new Context(); context.setVariables(view);
        String html = templateEngine.process("reports/maintenance-report", context);
        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            new PdfRendererBuilder().withHtmlContent(html, null).toStream(output).run(); return output.toByteArray();
        } catch (Exception exception) { throw new ReportValidationException("No fue posible generar el informe PDF."); }
    }

    private void validateName(String value, String message) { if (value == null || value.trim().isBlank() || value.trim().length() > 150) throw new ReportValidationException(message); }
    private void validateSignature(MultipartFile file, String label) { if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024 || !isPng(file)) throw new ReportValidationException(label + " no es válida."); }
    private void validatePhotos(List<MultipartFile> photos) { if (photos.size() > MAX_PHOTOS) throw new ReportValidationException("El informe admite máximo seis fotografías."); long total = 0; for (MultipartFile photo : photos) { if (photo.getSize() > MAX_IMAGE_BYTES || !isImage(photo)) throw new ReportValidationException("Una fotografía no es válida o supera el tamaño permitido."); total += photo.getSize(); } if (total > MAX_TOTAL_IMAGE_BYTES) throw new ReportValidationException("Las fotografías superan el tamaño total permitido."); }
    private boolean isPng(MultipartFile file) { try { byte[] b = file.getBytes(); return b.length > 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71; } catch (IOException e) { return false; } }
    private boolean isImage(MultipartFile file) { return isPng(file) || jpeg(file); }
    private boolean jpeg(MultipartFile file) { try { byte[] b = file.getBytes(); return b.length > 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255; } catch (IOException e) { return false; } }
    private String dataUri(MultipartFile file, String contentType) { try { return "data:" + (contentType == null ? "application/octet-stream" : contentType) + ";base64," + Base64.getEncoder().encodeToString(file.getBytes()); } catch (IOException e) { throw new ReportValidationException("No fue posible leer un archivo del informe."); } }
    private String safeFileName(String number) { return "Informe_OT_" + number.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf"; }
    private String sha256(byte[] value) { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value); return java.util.HexFormat.of().formatHex(digest); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String escapeOData(String value) { return value.replace("'", "''"); }
    private String first(String a, String b) { return a != null && !a.isBlank() ? a : b; }
    private String formatDateTime(String value) {
        if (value == null || value.isBlank()) return "No disponible";
        try { return OffsetDateTime.parse(value).atZoneSameInstant(REPORT_ZONE).format(REPORT_DATE_TIME); }
        catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(value).atZone(REPORT_ZONE).format(REPORT_DATE_TIME); }
        catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(value).atStartOfDay(REPORT_ZONE).format(REPORT_DATE_TIME); }
        catch (DateTimeParseException ignored) { return value; }
    }
    private String assetLabel(Integer asset) { return switch (asset == null ? 0 : asset) { case 1 -> "Localización"; case 2 -> "Equipamiento"; case 3 -> "Herramienta"; default -> "No disponible"; }; }
    private String stateLabel(String stateKey, String description) { if (description == null || description.isBlank()) return stateKey; return stateKey == null || stateKey.isBlank() ? description : stateKey + " - " + description; }
    private String assetCode(ValuekeepWorkOrderResource r) { int a = r.asset() != null ? r.asset() : r.assetType() == null ? 0 : r.assetType(); return switch (a) { case 1 -> r.location(); case 2 -> r.equipment(); case 3 -> r.tool(); default -> null; }; }
    private String assetLabel(ValuekeepWorkOrderResource r) { return joinLabel(assetCode(r), assetDescription(r)); }
    private String workshopLabel(String workshop, String description) { if (workshop == null || workshop.isBlank()) return description; if (description == null || description.isBlank()) return workshop; return workshop + " - " + description; }
    private String joinLabel(String code, String description) { if (code == null || code.isBlank()) return description; if (description == null || description.isBlank()) return code; return code + " - " + description; }
    private String logoDataUri() {
        try (var input = new ClassPathResource("reports/autoeastern_logo.jpeg").getInputStream()) {
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(input.readAllBytes());
        } catch (IOException exception) {
            throw new ReportValidationException("No fue posible cargar la marca del informe.");
        }
    }
    private String assetDescription(ValuekeepWorkOrderResource r) { int a = r.asset() != null ? r.asset() : r.assetType() == null ? 0 : r.assetType(); return switch (a) { case 1 -> first(r.locationDescription(), r.LocationDescription()); case 2 -> first(r.equipmentDescription(), r.EquipmentDescription()); case 3 -> first(r.toolDescription(), r.ToolDescription()); default -> null; }; }
    private List<Map<String, Object>> records(Map response) { Object value = response == null ? null : response.get("items"); if (!(value instanceof Collection<?> collection)) return List.of(); return collection.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList(); }
    private String value(Map<String, Object> record, String... keys) { for (String key : keys) if (record.get(key) != null) return String.valueOf(record.get(key)); return null; }
    private <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
    private PreviewArtifact requirePreview(String id, String workOrderId) { PreviewArtifact artifact = previews.get(id); if (artifact == null || artifact.expiresAt() < System.currentTimeMillis() || !workOrderId.equals(artifact.workOrderId())) { previews.remove(id); throw new ReportValidationException("La vista previa no existe o expiró. Genera una nueva vista previa."); } return artifact; }
    private void cleanupExpired() { long now = System.currentTimeMillis(); previews.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now); }

    public record GeneratedReport(byte[] pdf, ReportPreviewResponse response) { }
    private record VerifiedModel(ValuekeepWorkOrderResource workOrder, List<Map<String, Object>> tasks, String stateKey, String stateDescription, String workshopDescription) { }
    private static final class PreviewArtifact { private final String workOrderId, fileName, hash; private final byte[] pdf; private final long expiresAt; private boolean uploaded; PreviewArtifact(String w, String f, String h, byte[] p, long e) { workOrderId=w; fileName=f; hash=h; pdf=p; expiresAt=e; } String workOrderId(){return workOrderId;} String fileName(){return fileName;} byte[] pdf(){return pdf;} long expiresAt(){return expiresAt;} boolean uploaded(){return uploaded;} void markUploaded(){uploaded=true;} void markNotUploaded(){uploaded=false;} }
}
