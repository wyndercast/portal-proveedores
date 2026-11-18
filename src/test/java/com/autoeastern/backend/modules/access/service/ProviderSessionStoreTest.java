package com.autoeastern.backend.modules.access.service;

import com.autoeastern.backend.modules.access.config.ProviderAccessProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderSessionStoreTest {

    @Test
    void storesOnlyAnOpaqueTokenAndRefreshesActivity() {
        ProviderAccessProperties properties = new ProviderAccessProperties();
        properties.setInactivityTimeoutSeconds(1800);
        ProviderSessionStore store = new ProviderSessionStore(properties);

        ProviderSessionStore.CreatedSession created = store.create("work-order-id", "0000001917", "WORKER_01");

        assertTrue(created.token().length() >= 43);
        assertTrue(store.validateAndTouch(created.token()).isPresent());
        assertFalse(store.validateAndTouch("invalid-token").isPresent());
    }
}
