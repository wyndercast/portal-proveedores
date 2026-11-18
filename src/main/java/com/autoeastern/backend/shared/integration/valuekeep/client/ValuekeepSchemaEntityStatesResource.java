package com.autoeastern.backend.shared.integration.valuekeep.client;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record ValuekeepSchemaEntityStatesResource(
        List<Item> items,
        String nextPageLink,
        Integer count) {

    public record Item(
            @JsonAlias("StateKey") String stateKey,
            @JsonAlias("Description") String description,
            @JsonAlias("SchemaEntity") String schemaEntity) {
    }
}
