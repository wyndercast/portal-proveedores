package com.autoeastern.backend.shared.security;

import com.autoeastern.backend.modules.access.domain.ProviderAuthentication;
import com.autoeastern.backend.modules.access.service.ProviderSessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ProviderSessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderSessionAuthenticationFilter.class);
    private final ProviderSessionStore sessionStore;

    public ProviderSessionAuthenticationFilter(ProviderSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request.getHeader("Authorization"));
        if (token != null) {
            var session = sessionStore.validateAndTouch(token);
            if (session.isPresent()) {
                SecurityContextHolder.getContext().setAuthentication(new ProviderAuthentication(session.get()));
            } else {
                LOGGER.warn("Token de sesión de proveedor inválido o expirado para {}.", request.getRequestURI());
            }
        } else if (!"OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/api/v1/provider/")) {
            LOGGER.warn("Solicitud protegida sin token de sesión: {}.", request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }
}
