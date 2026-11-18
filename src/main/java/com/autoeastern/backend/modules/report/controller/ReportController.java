package com.autoeastern.backend.modules.report.controller;

import com.autoeastern.backend.modules.report.dto.ReportPreviewResponse;
import com.autoeastern.backend.modules.report.dto.ReportUploadResponse;
import com.autoeastern.backend.modules.report.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/v1/provider/work-order/report")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> preview(
            Authentication authentication,
            @RequestParam String technicianName,
            @RequestParam String receiverName,
            @RequestParam(defaultValue = "") String remarks,
            @RequestPart("technicianSignature") MultipartFile technicianSignature,
            @RequestPart("receiverSignature") MultipartFile receiverSignature,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        try {
            ReportService.GeneratedReport result = service.preview(authentication, technicianName, receiverName, remarks, technicianSignature, receiverSignature, photos).join();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                    .header("X-Report-Preview-Id", result.response().previewId())
                    .header("X-Report-File-Name", result.response().fileName())
                    .header("X-Report-Sha256", result.response().sha256()).body(result.pdf());
        } catch (CompletionException exception) {
            throw unwrap(exception);
        }
    }

    @GetMapping(value = "/preview/{previewId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getPreview(Authentication authentication, @PathVariable String previewId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).body(service.previewBytes(previewId, authentication));
    }

    @PostMapping("/upload")
    public ResponseEntity<ReportUploadResponse> upload(Authentication authentication, @RequestParam String previewId) {
        try {
            return ResponseEntity.ok(service.upload(authentication, previewId).join());
        } catch (CompletionException exception) {
            throw unwrap(exception);
        }
    }

    private RuntimeException unwrap(CompletionException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof RuntimeException runtimeException
                ? runtimeException
                : exception;
    }
}
