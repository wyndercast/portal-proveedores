package com.autoeastern.backend.modules.checklist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TaskVerificationBatchRequest(@NotBlank String operationId, @NotEmpty List<@Valid TaskVerificationRequest> responses) {
    public record TaskVerificationRequest(@NotBlank String taskInstanceId, @NotBlank String verificationTypeKey, String remarks) { }
}
