package com.autoeastern.backend.shared.integration.valuekeep.client;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ValuekeepWorkshopResource(
        String workshopKey,
        String description,
        @JsonAlias("Description") String Description) {

    public String resolvedDescription() {
        return description != null && !description.isBlank() ? description : Description;
    }
}
