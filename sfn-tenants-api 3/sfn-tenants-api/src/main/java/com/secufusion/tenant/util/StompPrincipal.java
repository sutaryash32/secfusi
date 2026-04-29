package com.secufusion.tenant.util;

import java.security.Principal;

/**
 * Simple Principal implementation for STOMP WebSocket sessions.
 * Wraps the user's keycloak ID (JWT sub claim) to enable user-targeted messaging.
 */
public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
