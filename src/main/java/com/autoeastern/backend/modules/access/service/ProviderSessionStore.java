package com.autoeastern.backend.modules.access.service;

import com.autoeastern.backend.modules.access.config.ProviderAccessProperties;
import com.autoeastern.backend.modules.access.domain.ProviderSession;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProviderSessionStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderSessionStore.class);
    private final Map<String, ProviderSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final ProviderAccessProperties properties;

    public ProviderSessionStore(ProviderAccessProperties properties) {
        this.properties = properties;
    }

    public CreatedSession create(String workOrderId, String workOrderNumber, String responsible) {
        removeExpired();
        if (sessions.size() >= properties.getMaxSessions()) {
            throw new ProviderAccessException("No hay capacidad disponible para crear una sesión.");
        }

        byte[] randomToken = new byte[32];
        secureRandom.nextBytes(randomToken);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        String tokenHash = hash(rawToken);
        long now = Instant.now().getEpochSecond();
        sessions.put(tokenHash, new ProviderSession(
                workOrderId, workOrderNumber, responsible,
                now,
                properties.getInactivityTimeoutSeconds()));
        LOGGER.info("Sesión creada; huella={}", fingerprint(tokenHash));
        return new CreatedSession(rawToken, properties.getInactivityTimeoutSeconds());
    }

    public Optional<ProviderSession> validateAndTouch(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hash(rawToken);
        ProviderSession session = sessions.get(tokenHash);
        if (session == null) {
            LOGGER.warn("Sesión no encontrada; huella={}", fingerprint(tokenHash));
            return Optional.empty();
        }
        long now = Instant.now().getEpochSecond();
        if (!session.touchIfActive(now, properties.getInactivityTimeoutSeconds())) {
            LOGGER.warn("Sesión expirada; huella={}", fingerprint(tokenHash));
            sessions.remove(tokenHash, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            sessions.remove(hash(rawToken));
        }
    }

    @Scheduled(fixedDelayString = "60000")
    public void removeExpired() {
        long now = Instant.now().getEpochSecond();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible en la JVM.", exception);
        }
    }

    private String fingerprint(String tokenHash) {
        return tokenHash.substring(0, Math.min(12, tokenHash.length()));
    }


    public record CreatedSession(String token, long expiresInSeconds) { }
}
