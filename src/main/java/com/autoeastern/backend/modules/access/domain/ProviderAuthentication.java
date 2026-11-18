package com.autoeastern.backend.modules.access.domain;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class ProviderAuthentication extends AbstractAuthenticationToken {
    private final ProviderSession session;

    public ProviderAuthentication(ProviderSession session) {
        super(Collections.emptyList());
        this.session = session;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return null; }

    @Override
    public Object getPrincipal() { return session; }

    public ProviderSession getSession() { return session; }
}
