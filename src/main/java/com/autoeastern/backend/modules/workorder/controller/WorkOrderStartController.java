package com.autoeastern.backend.modules.workorder.controller;

import com.autoeastern.backend.modules.workorder.dto.WorkOrderStartResponse;
import com.autoeastern.backend.modules.workorder.service.WorkOrderStartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/v1/provider/work-order")
public class WorkOrderStartController {
    private final WorkOrderStartService service;

    public WorkOrderStartController(WorkOrderStartService service) { this.service = service; }

    @PostMapping("/start")
    public ResponseEntity<WorkOrderStartResponse> start(Authentication authentication) {
        try {
            return ResponseEntity.ok(service.start(authentication).join());
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw exception;
        }
    }
}
