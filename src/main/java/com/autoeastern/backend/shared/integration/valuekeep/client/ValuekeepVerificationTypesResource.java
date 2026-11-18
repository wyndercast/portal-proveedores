package com.autoeastern.backend.shared.integration.valuekeep.client;

import java.util.List;

public record ValuekeepVerificationTypesResource(
        List<Item> items,
        String nextPageLink,
        Integer count) {

    public record Item(String verificationTypeKey, String description, Boolean remarksRequired) {
        public Item(String verificationTypeKey, String description) {
            this(verificationTypeKey, description, null);
        }
    }
}
