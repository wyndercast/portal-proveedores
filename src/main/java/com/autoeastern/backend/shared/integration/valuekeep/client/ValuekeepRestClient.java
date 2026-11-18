package com.autoeastern.backend.shared.integration.valuekeep.client;

import com.autoeastern.backend.shared.integration.valuekeep.auth.ValuekeepTokenService;
import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepClientException;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepRateLimitException;
import com.autoeastern.backend.shared.integration.valuekeep.exception.ValuekeepTimeoutException;
import com.autoeastern.backend.shared.integration.valuekeep.ratelimit.ValuekeepRateLimiter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValuekeepRestClient implements ValuekeepClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValuekeepRestClient.class);
    private final RestClient restClient;
    private final ValuekeepProperties properties;
    private final ValuekeepTokenService tokenService;
    private final ValuekeepRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public ValuekeepRestClient(
            RestClient restClient,
            ValuekeepProperties properties,
            ValuekeepTokenService tokenService,
            ValuekeepRateLimiter rateLimiter) {
        this.restClient = restClient;
        this.properties = properties;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(10, runnable -> {
            Thread thread = new Thread(runnable, "valuekeep-http-client");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public <T> CompletableFuture<T> get(String resourcePath, Class<T> responseType) {
        return request(HttpMethod.GET, resourcePath, null, responseType, 0);
    }

    @Override
    public <T> CompletableFuture<T> post(String resourcePath, Object requestBody, Class<T> responseType) {
        return request(HttpMethod.POST, resourcePath, requestBody, responseType, 0);
    }

    @Override
    public <T> CompletableFuture<T> put(String resourcePath, Object requestBody, Class<T> responseType) {
        return request(HttpMethod.PUT, resourcePath, requestBody, responseType, 0);
    }

    @Override
    public CompletableFuture<Void> uploadWorkOrderDocument(String workOrderId, String fileName, byte[] pdf) {
        return rateLimiter.acquire().thenApplyAsync(ignored -> {
            String token = tokenService.getAccessToken();
            try {
                MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
                HttpEntity<byte[]> document = new HttpEntity<>(pdf, new org.springframework.http.HttpHeaders());
                document.getHeaders().setContentType(MediaType.APPLICATION_PDF);
                document.getHeaders().setContentDispositionFormData("document", fileName);
                parts.add("document", document);
                URI uploadUri = buildUploadUri(workOrderId);
                LOGGER.info("Valuekeep upload request PUT {} workOrderId={} fileName={} bytes={}",
                        uploadUri, workOrderId, fileName, pdf.length);
                restClient.put().uri(uploadUri)
                        .headers(headers -> { headers.setBearerAuth(token); headers.setContentType(MediaType.MULTIPART_FORM_DATA); })
                        .body(parts)
                        .exchange((request, response) -> {
                            int status = response.getStatusCode().value();
                            LOGGER.info("Valuekeep upload response {} workOrderId={} allow={}",
                                    status, workOrderId, response.getHeaders().getAllow());
                            if (status < 200 || status >= 300) {
                                String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                                throw new ValuekeepClientException(
                                        "Valuekeep rechazó la carga del informe con HTTP " + status
                                                + (responseBody.isBlank() ? "" : ": " + summarize(responseBody)),
                                        status);
                            }
                            return null;
                        });
                return null;
            } catch (ResourceAccessException exception) {
                throw new ValuekeepTimeoutException("Timeout al cargar el informe en Valuekeep.", exception);
            } catch (ValuekeepClientException exception) {
                if (exception.getStatusCode() == 401) tokenService.invalidate(token);
                throw exception;
            } catch (Exception exception) {
                throw new ValuekeepClientException("No fue posible cargar el informe en Valuekeep.", 502, exception);
            }
        }, executor);
    }

    private <T> CompletableFuture<T> request(
            HttpMethod method,
            String resourcePath,
            Object requestBody,
            Class<T> responseType,
            int retryAttempt) {
        CompletableFuture<T> attempt = rateLimiter.acquire()
                .thenApplyAsync(ignored -> executeOnce(method, resourcePath, requestBody, responseType), executor);

        CompletableFuture<CompletableFuture<T>> handled = attempt.handle((value, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.<T>completedFuture(value);
            }

            Throwable cause = unwrap(throwable);
            if (!(cause instanceof ValuekeepClientException exception)) {
                return this.<T>failedFuture(cause);
            }

            boolean safeToRetry = method == HttpMethod.GET || method == HttpMethod.HEAD;
            if (safeToRetry && retryAttempt == 0 && exception.getStatusCode() == 401) {
                return request(method, resourcePath, requestBody, responseType, retryAttempt + 1);
            }

            Long retryAfter = exception.getRetryAfterSeconds();
            if (safeToRetry && retryAttempt == 0 && exception.getStatusCode() == 429 && retryAfter != null) {
                Duration delay = Duration.ofSeconds(retryAfter);
                if (!delay.isNegative() && delay.getSeconds() <= properties.getRateLimitWaitSeconds()) {
                    return CompletableFuture.runAsync(
                            () -> { },
                            CompletableFuture.delayedExecutor(delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS))
                            .thenCompose(ignored -> this.<T>request(
                                    method, resourcePath, requestBody, responseType, retryAttempt + 1));
                }
            }
            return this.<T>failedFuture(cause);
        });
        return handled.thenCompose(future -> future);
    }

    private <T> T executeOnce(HttpMethod method, String resourcePath, Object requestBody, Class<T> responseType) {
        long started = System.nanoTime();
        URI requestUri = buildUri(resourcePath);
        LOGGER.info("Valuekeep request {} {}", method, requestUri);
        String token = tokenService.getAccessToken();
        try {
            return restClient.method(method)
                    .uri(requestUri)
                    .headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
                        if (requestBody != null) {
                            headers.setContentType(MediaType.APPLICATION_JSON);
                        }
                    })
                    .body(requestBody == null ? "" : requestBody)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        LOGGER.info("Valuekeep response {} {} in {} ms", status, resourcePath == null ? "" : resourcePath.split("\\?", 2)[0], (System.nanoTime() - started) / 1_000_000);
                        if (status < 200 || status >= 300) {
                            String responseBody = response.getBody() == null
                                    ? ""
                                    : new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            String allow = response.getHeaders().getAllow().isEmpty()
                                    ? (status == 405 ? " El proveedor no permite este método para la ruta solicitada." : "")
                                    : " Métodos permitidos: " + response.getHeaders().getAllow().stream()
                                            .map(HttpMethod::name)
                                            .collect(Collectors.joining(", "));
                            String providerDetail = responseBody.isBlank() ? "" : ": " + summarize(responseBody);
                            throw new ValuekeepClientException(
                                    "Valuekeep rechazó " + method + " " + resourcePath + " con HTTP " + status
                                            + allow + providerDetail,
                                    status,
                                    parseRetryAfter(response.getHeaders().getFirst("Retry-After")));
                        }
                        if (responseType == Void.class || response.getBody() == null) {
                            return null;
                        }
                        try {
                            return objectMapper.readValue(response.getBody(), responseType);
                        } catch (JacksonException exception) {
                            throw new ValuekeepClientException(
                                    "La respuesta de Valuekeep no tiene un formato válido.", 502, exception);
                        }
                    });
        } catch (ResourceAccessException exception) {
            LOGGER.warn("Timeout o error de red en Valuekeep para {} {}: {}", method, resourcePath, exception.getClass().getSimpleName());
            throw new ValuekeepTimeoutException("Timeout al comunicarse con Valuekeep.", exception);
        } catch (ValuekeepClientException exception) {
            LOGGER.warn("Valuekeep rechazó method={} resource={} status={} detalle={}",
                    method, resourcePath, exception.getStatusCode(), exception.getMessage());
            if (exception.getStatusCode() == 401) {
                tokenService.invalidate(token);
            }
            throw exception;
        } catch (Exception exception) {
            throw new ValuekeepClientException("Error al comunicarse con Valuekeep.", 502, exception);
        }
    }

    private URI buildUri(String resourcePath) {
        String normalizedPath = resourcePath == null ? "" : resourcePath.replaceFirst("^/", "");
        String[] pathAndQuery = normalizedPath.split("\\?", 2);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                // base-url already points to Valuekeep's API root, for example:
                // https://cloud.valuekeep.com/api
                // Keep the /app root reserved for multipart document uploads.
                .pathSegment(properties.getTenantKey(), properties.getOrgKey());
        if (!pathAndQuery[0].isBlank()) {
            builder.pathSegment(pathAndQuery[0].split("/"));
        }
        if (pathAndQuery.length == 2 && !pathAndQuery[1].isBlank()) {
            for (String parameter : pathAndQuery[1].split("&")) {
                String[] nameAndValue = parameter.split("=", 2);
                if (nameAndValue.length == 2) {
                    builder.queryParam(nameAndValue[0], nameAndValue[1]);
                }
            }
        }
        return builder.build().encode().toUri();
    }

    private URI buildUploadUri(String workOrderId) {
        return UriComponentsBuilder.fromUriString(properties.getUploadBaseUrl())
                .pathSegment(properties.getTenantKey(), properties.getOrgKey(), "maintenance", "workorders", workOrderId, "upload")
                .build().encode().toUri();
    }

    private Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String summarize(String responseBody) {
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 800 ? normalized : normalized.substring(0, 800) + "…";
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        return throwable;
    }

    private <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
