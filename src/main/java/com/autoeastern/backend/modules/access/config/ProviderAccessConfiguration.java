package com.autoeastern.backend.modules.access.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ProviderAccessProperties.class)
public class ProviderAccessConfiguration {
}
