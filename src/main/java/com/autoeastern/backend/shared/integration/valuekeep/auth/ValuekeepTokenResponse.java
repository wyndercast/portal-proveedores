package com.autoeastern.backend.shared.integration.valuekeep.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ValuekeepTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        String scope) {
}
