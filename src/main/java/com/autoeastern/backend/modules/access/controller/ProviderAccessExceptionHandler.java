package com.autoeastern.backend.modules.access.controller;

import com.autoeastern.backend.modules.access.service.ProviderAccessException;
import com.autoeastern.backend.modules.access.service.ProviderAccessStateException;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepAuthenticationException;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepClientException;
import com.autoeastern.backend.modules.report.service.ReportValidationException;
import com.autoeastern.backend.modules.finalization.service.FinalizationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ProviderAccessExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderAccessExceptionHandler.class);
    @ExceptionHandler(ProviderAccessException.class)
    ProblemDetail handleProviderAccess(ProviderAccessException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(ProviderAccessStateException.class)
    ProblemDetail handleProviderAccessState(ProviderAccessStateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("code", "WORK_ORDER_STATE_NOT_AVAILABLE");
        return problem;
    }

    @ExceptionHandler(CompletionException.class)
    ProblemDetail handleCompletion(CompletionException exception) {
        if (exception.getCause() instanceof ProviderAccessException providerAccessException) {
            return handleProviderAccess(providerAccessException);
        }
        if (exception.getCause() instanceof ProviderAccessStateException stateException) {
            return handleProviderAccessState(stateException);
        }
        if (exception.getCause() instanceof ValuekeepAuthenticationException
                || exception.getCause() instanceof ValuekeepClientException) {
            Throwable cause = exception.getCause();
            if (cause instanceof ValuekeepClientException clientException) {
                LOGGER.warn("Fallo Valuekeep durante autorización: HTTP {} ({})", clientException.getStatusCode(), clientException.getClass().getSimpleName());
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_GATEWAY,
                        "No fue posible consultar Valuekeep durante la autorización.");
                problem.setProperty("code", clientException.getClass().getSimpleName().equals("ValuekeepTimeoutException")
                        ? "VALUEKEEP_TIMEOUT" : "VALUEKEEP_HTTP_" + clientException.getStatusCode());
                return problem;
            }
            LOGGER.warn("Fallo de autenticación contra Valuekeep: {}", cause.getClass().getSimpleName());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_GATEWAY,
                    "No fue posible autenticar contra Valuekeep.");
            problem.setProperty("code", "VALUEKEEP_AUTH");
            return problem;
        }
        if (exception.getCause() instanceof ReportValidationException reportValidationException) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, reportValidationException.getMessage());
        }
        if (exception.getCause() instanceof FinalizationException finalizationException) {
            return ProblemDetail.forStatusAndDetail(finalizationException.getStatus(), finalizationException.getMessage());
        }
        throw exception;
    }
}
