package com.autoeastern.backend.modules.workorder.controller;

import com.autoeastern.backend.modules.workorder.dto.WorkOrderResponse;
import com.autoeastern.backend.modules.workorder.service.WorkOrderQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/v1/provider/work-order")
public class WorkOrderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkOrderController.class);
    private final WorkOrderQueryService queryService;

    public WorkOrderController(WorkOrderQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<WorkOrderResponse> getWorkOrder(Authentication authentication) {
        LOGGER.info("Iniciando respuesta de work-order; autenticado={}",
                authentication != null && authentication.isAuthenticated());
        try {
            WorkOrderResponse response = queryService.getAuthorizedWorkOrder(authentication).join();
            LOGGER.info("Respuesta de work-order construida; planes={}", response.maintenancePlans().size());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .body(response);
        } catch (CompletionException exception) {
            LOGGER.error("Error completando respuesta de work-order", exception.getCause());
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("Error construyendo respuesta de work-order", exception);
            throw exception;
        }
    }
}
