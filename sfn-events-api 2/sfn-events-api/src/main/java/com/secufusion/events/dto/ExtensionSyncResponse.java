package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for extension sync with policy evaluation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionSyncResponse {

    /**
     * Whether the sync was successful
     */
    private Boolean success;

    /**
     * Message describing the result
     */
    private String message;

    /**
     * Timestamp of the sync
     */
    private LocalDateTime syncTimestamp;

    /**
     * Device ID (for authenticated devices)
     */
    private String deviceId;

    /**
     * Device token (for anonymous devices)
     */
    private String deviceToken;

    /**
     * Total extensions processed
     */
    private Integer totalExtensions;

    /**
     * Number of allowed extensions
     */
    private Integer allowedCount;

    /**
     * Number of blocked extensions
     */
    private Integer blockedCount;

    /**
     * Number of warned extensions
     */
    private Integer warnedCount;

    /**
     * Policy evaluation results for each extension
     */
    private List<ExtensionPolicyResult> results;

    /**
     * Overall policy config for the extension
     */
    private PolicyConfig policyConfig;

    /**
     * Individual extension policy result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtensionPolicyResult {
        /**
         * Extension ID
         */
        private String extensionId;

        /**
         * Extension name
         */
        private String extensionName;

        /**
         * Policy action: ALLOW, BLOCK, WARN
         */
        private String action;

        /**
         * Reason for the action
         */
        private String reason;

        /**
         * ID of the policy rule that matched
         */
        private String matchedPolicyId;

        /**
         * Whether extension is whitelisted
         */
        private Boolean isWhitelisted;

        /**
         * Whether extension is blacklisted
         */
        private Boolean isBlacklisted;

        /**
         * Calculated risk level
         */
        private String riskLevel;

        /**
         * Risk score (0-100)
         */
        private Integer riskScore;

        /**
         * High-risk permissions found
         */
        private List<String> highRiskPermissions;

        /**
         * Warning message to show user (if action is WARN)
         */
        private String warningMessage;
    }

    /**
     * Policy configuration to send to extension
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyConfig {
        /**
         * Policy ID
         */
        private String policyId;

        /**
         * Policy version
         */
        private String policyVersion;

        /**
         * Default action for unknown extensions
         */
        private String defaultAction;

        /**
         * Action for unknown extensions
         */
        private String unknownExtensionAction;

        /**
         * Warning message template
         */
        private String warningMessage;

        /**
         * Whether to block on high-risk permissions
         */
        private Boolean blockHighRisk;

        /**
         * Sync interval in seconds
         */
        private Integer syncIntervalSeconds;
    }
}
