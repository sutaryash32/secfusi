package com.secufusion.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for syncing extension data from browser to server.
 * Can be used with or without authentication (anonymous or authenticated device).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionSyncRequest {

    /**
     * Device token for anonymous devices (mutually exclusive with deviceId for authenticated users).
     */
    private String deviceToken;

    /**
     * Device ID for authenticated users.
     */
    private String deviceId;

    /**
     * Tenant code for identifying the tenant (for MSI deployments).
     */
    private String tenantCode;

    /**
     * Browser type (Chrome, Edge, Firefox, etc.)
     */
    @NotBlank(message = "Browser type is required")
    private String browserType;

    /**
     * Browser version
     */
    private String browserVersion;

    /**
     * SecuFusion extension version
     */
    private String extensionVersion;

    /**
     * List of installed extensions
     */
    @NotNull(message = "Extensions list is required")
    private List<ExtensionInfo> extensions;

    /**
     * Nested class for extension information from browser
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtensionInfo {
        /**
         * Chrome/Edge extension ID
         */
        @NotBlank(message = "Extension ID is required")
        private String extensionId;

        /**
         * Display name
         */
        private String name;

        /**
         * Installed version
         */
        private String version;

        /**
         * Description from manifest
         */
        private String description;

        /**
         * Homepage URL
         */
        private String homepageUrl;

        /**
         * Icon URL
         */
        private String iconUrl;

        /**
         * Whether extension is currently enabled
         */
        private Boolean enabled;

        /**
         * Permissions from manifest
         */
        private List<String> permissions;

        /**
         * Host permissions (URLs extension can access)
         */
        private List<String> hostPermissions;

        /**
         * Optional permissions
         */
        private List<String> optionalPermissions;

        /**
         * How extension was installed: normal, admin, development, sideload
         */
        private String installType;

        /**
         * Whether managed by enterprise policy
         */
        private Boolean isManaged;

        /**
         * Whether user can disable it
         */
        private Boolean mayDisable;

        /**
         * Offline enabled flag
         */
        private Boolean offlineEnabled;
    }
}
