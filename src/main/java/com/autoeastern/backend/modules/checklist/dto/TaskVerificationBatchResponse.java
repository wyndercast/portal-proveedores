package com.autoeastern.backend.modules.checklist.dto;

import java.util.List;

public record TaskVerificationBatchResponse(String operationId, Summary summary, List<Result> results) {
    public record Summary(int saved, int failed, int uncertain) { }
    public record Result(String taskInstanceId, String status, String message) { }
}
