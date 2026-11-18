package com.autoeastern.backend.modules.access.controller;

import com.autoeastern.backend.modules.access.dto.ProviderAccessRequest;
import com.autoeastern.backend.modules.access.dto.ProviderAccessResponse;
import com.autoeastern.backend.modules.access.service.ProviderAccessService;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/provider-access")
public class ProviderAccessController {
    private final ProviderAccessService accessService;
    private final ValuekeepProperties valuekeepProperties;

    public ProviderAccessController(ProviderAccessService accessService, ValuekeepProperties valuekeepProperties) {
        this.accessService = accessService;
        this.valuekeepProperties = valuekeepProperties;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<ProviderAccessResponse>> authorize(
            @Valid @RequestBody ProviderAccessRequest request,
            HttpServletRequest servletRequest) {
        return accessService.authorize(request, servletRequest.getRemoteAddr(), valuekeepProperties.getCompanyKey())
                .thenApply(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore().mustRevalidate())
                        .body(response));
    }

    @DeleteMapping
    public ResponseEntity<Void> revoke(@RequestHeader(value = "Authorization", required = false) String authorization) {
        accessService.revoke(extractBearerToken(authorization));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .build();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
