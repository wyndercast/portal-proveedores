package com.autoeastern.backend.shared.integration.valuekeep.config;

import com.autoeastern.backend.shared.integration.valuekeep.auth.ValuekeepTokenService;
import com.autoeastern.backend.shared.integration.valuekeep.client.ValuekeepRestClient;
import com.autoeastern.backend.shared.integration.valuekeep.ratelimit.ValuekeepRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(ValuekeepProperties.class)
public class ValuekeepIntegrationConfiguration {

    @Bean
    RestClient valuekeepRestClient(ValuekeepProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    RestClient valuekeepTokenRestClient(ValuekeepProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    ValuekeepRateLimiter valuekeepRateLimiter(ValuekeepProperties properties) {
        return new ValuekeepRateLimiter(properties.getRateLimit(), properties.getRateLimitWaitSeconds());
    }

    @Bean
    ValuekeepTokenService valuekeepTokenService(
            @Qualifier("valuekeepTokenRestClient") RestClient valuekeepTokenRestClient,
            ValuekeepProperties properties) {
        return new ValuekeepTokenService(valuekeepTokenRestClient, properties);
    }

    @Bean
    ValuekeepRestClient valuekeepClient(
            @Qualifier("valuekeepRestClient") RestClient valuekeepRestClient,
            ValuekeepProperties properties,
            ValuekeepTokenService tokenService,
            ValuekeepRateLimiter rateLimiter) {
        return new ValuekeepRestClient(valuekeepRestClient, properties, tokenService, rateLimiter);
    }
}
