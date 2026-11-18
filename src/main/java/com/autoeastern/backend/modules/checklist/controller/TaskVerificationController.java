package com.autoeastern.backend.modules.checklist.controller;

import com.autoeastern.backend.modules.checklist.dto.TaskVerificationBatchRequest;
import com.autoeastern.backend.modules.checklist.dto.TaskVerificationBatchResponse;
import com.autoeastern.backend.modules.checklist.service.TaskVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/v1/provider/work-order/task-verifications")
public class TaskVerificationController {
    private final TaskVerificationService service;
    public TaskVerificationController(TaskVerificationService service) { this.service = service; }
    @PostMapping("/batch")
    public ResponseEntity<TaskVerificationBatchResponse> saveBatch(Authentication authentication, @Valid @RequestBody TaskVerificationBatchRequest request) {
        try {
            TaskVerificationBatchResponse response = service.saveBatch(authentication, request).join();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }
}
