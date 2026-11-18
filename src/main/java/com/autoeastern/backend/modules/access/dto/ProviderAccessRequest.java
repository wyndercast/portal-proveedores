package com.autoeastern.backend.modules.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProviderAccessRequest(
        @NotBlank @Size(max = 100) String workOrderNumber,
        @NotBlank @Size(max = 100) String workerCode) {
}
