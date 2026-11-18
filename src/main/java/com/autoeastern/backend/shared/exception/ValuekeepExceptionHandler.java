package com.autoeastern.backend.shared.exception;

import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepAuthenticationException;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepClientException;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepRateLimitException;
import com.autoeastern.backend.modules.checklist.service.TaskVerificationException;
import com.autoeastern.backend.modules.report.service.ReportValidationException;
import com.autoeastern.backend.modules.finalization.service.FinalizationException;
import com.autoeastern.backend.modules.workorder.service.WorkOrderStartService;
import com.autoeastern.backend.modules.workorder.service.WorkOrderAccessStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ValuekeepExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValuekeepExceptionHandler.class);

    @ExceptionHandler(TaskVerificationException.class)
    ProblemDetail handleVerification(TaskVerificationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ReportValidationException.class)
    ProblemDetail handleReportValidation(ReportValidationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(FinalizationException.class)
    ProblemDetail handleFinalization(FinalizationException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(WorkOrderStartService.WorkOrderStartException.class)
    ProblemDetail handleWorkOrderStart(WorkOrderStartService.WorkOrderStartException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(WorkOrderAccessStateException.class)
    ProblemDetail handleWorkOrderAccessState(WorkOrderAccessStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ValuekeepRateLimitException.class)
    ProblemDetail handleRateLimit(ValuekeepRateLimitException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    @ExceptionHandler(ValuekeepAuthenticationException.class)
    ProblemDetail handleAuthentication(ValuekeepAuthenticationException exception) {
        LOGGER.warn("Fallo de autenticación contra Valuekeep: {}", exception.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "No fue posible autenticar con el proveedor externo.");
    }

    @ExceptionHandler(ValuekeepClientException.class)
    ProblemDetail handleClient(ValuekeepClientException exception) {
        LOGGER.warn("Fallo HTTP de Valuekeep: status={} tipo={} detalle={}",
                exception.getStatusCode(), exception.getClass().getSimpleName(), exception.getMessage());
        HttpStatus status = exception.getStatusCode() == 429
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.BAD_GATEWAY;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                exception.getStatusCode() == 429
                        ? "El proveedor está temporalmente ocupado. Intenta nuevamente más tarde."
                        : "No fue posible completar la operación con el proveedor externo.");
        problem.setProperty("code", exception.getClass().getSimpleName().equals("ValuekeepTimeoutException")
                ? "VALUEKEEP_TIMEOUT" : "VALUEKEEP_HTTP_" + exception.getStatusCode());
        return problem;
    }
}
