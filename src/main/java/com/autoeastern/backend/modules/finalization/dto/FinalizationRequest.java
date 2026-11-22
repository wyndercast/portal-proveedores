package com.autoeastern.backend.modules.finalization.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record FinalizationRequest(@NotBlank String previewId, @AssertTrue boolean confirmation) { }
