package com.autoeastern.backend.modules.finalization.dto;

public record FinalizationResponse(String result, String pdfStatus, String closureStatus, String remoteStateKey, String message) { }
