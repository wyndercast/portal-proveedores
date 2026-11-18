package com.autoeastern.backend.shared.integration.valuekeep.auth;

import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepAuthenticationException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class ValuekeepTokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValuekeepTokenService.class);
    private static final long EXPIRY_SKEW_SECONDS = 30;

    private final RestClient restClient;
    private final ValuekeepProperties properties;
    private final Object refreshMonitor = new Object();
    private volatile CachedToken cachedToken;

    public ValuekeepTokenService(RestClient restClient, ValuekeepProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isUsable()) {
            return current.value();
        }

        synchronized (refreshMonitor) {
            current = cachedToken;
            if (current != null && current.isUsable()) {
                return current.value();
            }
            cachedToken = requestToken();
            return cachedToken.value();
        }
    }

    public void invalidate(String token) {
        synchronized (refreshMonitor) {
            if (cachedToken != null && cachedToken.value().equals(token)) {
                cachedToken = null;
            }
        }
    }

    private CachedToken requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        try {
            ValuekeepTokenResponse response = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(ValuekeepTokenResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ValuekeepAuthenticationException("Primavera Identity no devolvió un token válido.");
            }
            long expiresIn = Math.max(1, response.expiresIn());
            LOGGER.info("Token OAuth2 de Valuekeep obtenido correctamente; expiración en {} segundos.", expiresIn);
            return new CachedToken(response.accessToken(), Instant.now().plusSeconds(expiresIn));
        } catch (ValuekeepAuthenticationException exception) {
            LOGGER.warn("Valuekeep rechazó la autenticación OAuth2: {}", exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("Falló la solicitud OAuth2 contra Valuekeep en {}: {}", properties.getTokenUrl(), exception.getClass().getSimpleName());
            throw new ValuekeepAuthenticationException(
                    "No fue posible obtener el token de Valuekeep.", exception);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isUsable() {
            return Instant.now().isBefore(expiresAt.minusSeconds(EXPIRY_SKEW_SECONDS));
        }
    }
}
