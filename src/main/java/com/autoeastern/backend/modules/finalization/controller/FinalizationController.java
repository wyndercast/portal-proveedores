package com.autoeastern.backend.modules.finalization.controller;

import com.autoeastern.backend.modules.finalization.dto.FinalizationRequest;
import com.autoeastern.backend.modules.finalization.dto.FinalizationResponse;
import com.autoeastern.backend.modules.finalization.service.FinalizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/v1/provider/work-order")
public class FinalizationController {
    private final FinalizationService service;
    public FinalizationController(FinalizationService service) { this.service = service; }
    @PostMapping("/finalize")
    public ResponseEntity<FinalizationResponse> finalize(Authentication authentication, @Valid @RequestBody FinalizationRequest request, HttpServletRequest servletRequest) {
        String authorization = servletRequest.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        try {
            return ResponseEntity.ok(service.finalize(authentication, request, token).join());
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }
}
