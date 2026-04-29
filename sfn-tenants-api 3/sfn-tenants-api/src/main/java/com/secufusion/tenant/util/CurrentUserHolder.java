package com.secufusion.tenant.util;

import lombok.Getter;
import lombok.Setter;

/**
 * Thread-local holder for current user context.
 * Used by audit listeners to capture tenant and user information during entity operations.
 *
 * This is populated by the JwtTenantUserValidationFilter and cleared after request completion.
 */
public class CurrentUserHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private CurrentUserHolder() {
        // Utility class - prevent instantiation
    }

    public static void setContext(String tenantId, String userId, String username) {
        CONTEXT.set(new UserContext(tenantId, userId, username));
    }

    public static String getTenantId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    public static String getUserId() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    public static String getUsername() {
        UserContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUsername() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    @Getter
    @Setter
    public static class UserContext {
        private final String tenantId;
        private final String userId;
        private final String username;

        public UserContext(String tenantId, String userId, String username) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.username = username;
        }
    }
}
