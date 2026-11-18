package com.autoeastern.backend.shared.security;

import com.autoeastern.backend.modules.access.service.ProviderSessionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfiguration.class);
    @Value("${portal.frontend-origins:http://localhost:4200}")
    private String frontendOrigins;
    @Value("${portal.frontend-origin-patterns:}")
    private String frontendOriginPatterns;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(frontendOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedOriginPatterns(Arrays.stream(frontendOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .toList());
        LOGGER.info("CORS configurado para orígenes explícitos y {} patrones de origen.",
                configuration.getAllowedOriginPatterns().size());
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain providerSecurityFilterChain(
            HttpSecurity http,
            ProviderSessionStore sessionStore) throws Exception {
        return http
                .cors(cors -> { })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                                LOGGER.warn("Solicitud no autenticada rechazada: {} {}, authorizationPresent={}",
                                        request.getMethod(), request.getRequestURI(),
                                        request.getHeader("Authorization") != null);
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión de proveedor requerida");
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/api/v1/provider-access").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new ProviderSessionAuthenticationFilter(sessionStore),
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
