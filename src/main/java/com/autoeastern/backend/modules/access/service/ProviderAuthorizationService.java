package com.autoeastern.backend.modules.access.service;

import com.autoeastern.backend.modules.access.domain.ProviderAuthentication;
import com.autoeastern.backend.modules.access.domain.ProviderSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ProviderAuthorizationService {
    public ProviderSession requireAuthenticatedSession(Authentication authentication) {
        if (!(authentication instanceof ProviderAuthentication providerAuthentication)) {
            throw new ProviderAccessException("Autorización de proveedor requerida.");
        }
        return providerAuthentication.getSession();
    }

    public ProviderSession requireAccess(Authentication authentication, String workOrderNumber) {
        ProviderSession session = requireAuthenticatedSession(authentication);
        if (!session.getWorkOrderNumber().equals(workOrderNumber)) {
            throw new ProviderAccessException("La sesión no autoriza la orden solicitada.");
        }
        return session;
    }
}
