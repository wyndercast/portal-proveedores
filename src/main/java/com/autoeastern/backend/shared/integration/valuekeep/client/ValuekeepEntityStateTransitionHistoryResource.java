package com.autoeastern.backend.shared.integration.valuekeep.client;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record ValuekeepEntityStateTransitionHistoryResource(
        @JsonAlias({"Items", "value", "Value"}) List<Item> items,
        String nextPageLink,
        Integer count) {

    public List<Item> records() {
        return items == null ? List.of() : items;
    }

    public record Item(
            @JsonAlias("NewState") String newState,
            @JsonAlias("DateTransition") String dateTransition,
            @JsonAlias("EntityNaturalKey") String entityNaturalKey,
            @JsonAlias("SchemaEntity") String schemaEntity) {
    }
}
