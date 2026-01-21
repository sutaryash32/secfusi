package com.secufusion.iam.service;

import com.secufusion.iam.dto.AccessLevelResponse;
import com.secufusion.iam.dto.RetentionPeriodResponse;
import com.secufusion.iam.dto.TenantFeatureAccessResponse;
import com.secufusion.iam.exception.FeatureNotAvailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility class for checking feature access based on tenant's subscription package.
 *
 * Usage:
 * <pre>
 * {@code
 * @Autowired
 * private FeatureAccessChecker featureChecker;
 *
 * public void someMethod(String tenantId) {
 *     // Simple check - throws exception if no access
 *     featureChecker.requireFeature(tenantId, "DLP_BLOCK_UPLOADS");
 *
 *     // Check with custom message
 *     featureChecker.requireFeature(tenantId, "DLP_BLOCK_UPLOADS", "Basic");
 *
 *     // Boolean check - returns true/false
 *     if (featureChecker.hasAccess(tenantId, "SECOPS_MITRE_MAPPING")) {
 *         // do something
 *     }
 *
 *     // Get access level for tiered functionality
 *     String level = featureChecker.getAccessLevel(tenantId, "DLP_BLOCK_UPLOADS");
 *     if ("ADVANCED".equals(level)) {
 *         // advanced functionality
 *     }
 *
 *     // Get retention days for data storage
 *     Integer days = featureChecker.getRetentionDays(tenantId, "SECOPS_SECURITY_EVENTS");
 * }
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeatureAccessChecker {

    private final TenantFeatureService tenantFeatureService;

    /**
     * Check if tenant has access to a feature.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code to check
     * @return true if tenant has access, false otherwise
     */
    public boolean hasAccess(String tenantId, String featureCode) {
        try {
            return tenantFeatureService.hasFeatureAccess(tenantId, featureCode);
        } catch (Exception e) {
            log.error("Error checking feature access for tenant {} and feature {}: {}",
                    tenantId, featureCode, e.getMessage());
            return false;
        }
    }

    /**
     * Require feature access - throws exception if not available.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code to check
     * @throws FeatureNotAvailableException if feature is not available
     */
    public void requireFeature(String tenantId, String featureCode) {
        if (!hasAccess(tenantId, featureCode)) {
            log.warn("Feature access denied: tenant={}, feature={}", tenantId, featureCode);
            throw new FeatureNotAvailableException(featureCode, tenantId);
        }
    }

    /**
     * Require feature access with upgrade suggestion.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code to check
     * @param requiredPackage The minimum package required for this feature
     * @throws FeatureNotAvailableException if feature is not available
     */
    public void requireFeature(String tenantId, String featureCode, String requiredPackage) {
        if (!hasAccess(tenantId, featureCode)) {
            String currentPackage = tenantFeatureService.getTenantPackageName(tenantId);
            log.warn("Feature access denied: tenant={}, feature={}, currentPackage={}, requiredPackage={}",
                    tenantId, featureCode, currentPackage, requiredPackage);
            throw new FeatureNotAvailableException(featureCode, tenantId, currentPackage, requiredPackage);
        }
    }

    /**
     * Get the access level code for a feature.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return The access level code (NO, LIMITED, BASIC, YES, ADVANCED, etc.) or null if not found
     */
    public String getAccessLevel(String tenantId, String featureCode) {
        AccessLevelResponse response = tenantFeatureService.getFeatureAccessLevel(tenantId, featureCode);
        return response != null ? response.getLevelCode() : null;
    }

    /**
     * Get the access level value for a feature.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return The access level value (0-7) or 0 if not found
     */
    public int getAccessLevelValue(String tenantId, String featureCode) {
        AccessLevelResponse response = tenantFeatureService.getFeatureAccessLevel(tenantId, featureCode);
        return response != null ? response.getLevelValue() : 0;
    }

    /**
     * Check if access level is at least the specified level.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @param minimumLevel The minimum access level required (e.g., "ADVANCED")
     * @return true if access level is at or above minimum
     */
    public boolean hasMinimumAccessLevel(String tenantId, String featureCode, String minimumLevel) {
        String currentLevel = getAccessLevel(tenantId, featureCode);
        if (currentLevel == null) {
            return false;
        }

        int currentValue = getAccessLevelValue(tenantId, featureCode);
        int minimumValue = getAccessLevelValueByCode(minimumLevel);

        return currentValue >= minimumValue;
    }

    /**
     * Get retention period in days for a feature.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return The retention period in days, or null if not set
     */
    public Integer getRetentionDays(String tenantId, String featureCode) {
        RetentionPeriodResponse response = tenantFeatureService.getFeatureRetention(tenantId, featureCode);
        return response != null ? response.getPeriodDays() : null;
    }

    /**
     * Get retention period code for a feature.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return The retention period code (2_DAYS, 30_DAYS, 12_MONTHS, etc.) or null
     */
    public String getRetentionPeriodCode(String tenantId, String featureCode) {
        RetentionPeriodResponse response = tenantFeatureService.getFeatureRetention(tenantId, featureCode);
        return response != null ? response.getPeriodCode() : null;
    }

    /**
     * Get all feature access details for a tenant.
     *
     * @param tenantId The tenant identifier
     * @return List of all feature access information
     */
    public List<TenantFeatureAccessResponse.FeatureAccess> getAllFeatures(String tenantId) {
        return tenantFeatureService.getAllFeatureAccess(tenantId);
    }

    /**
     * Check if feature is LIMITED access (restricted functionality).
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return true if access level is LIMITED
     */
    public boolean isLimitedAccess(String tenantId, String featureCode) {
        return "LIMITED".equals(getAccessLevel(tenantId, featureCode));
    }

    /**
     * Check if feature is BASIC access.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return true if access level is BASIC
     */
    public boolean isBasicAccess(String tenantId, String featureCode) {
        return "BASIC".equals(getAccessLevel(tenantId, featureCode));
    }

    /**
     * Check if feature has ADVANCED or higher access.
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return true if access level is ADVANCED or higher
     */
    public boolean isAdvancedAccess(String tenantId, String featureCode) {
        int value = getAccessLevelValue(tenantId, featureCode);
        return value >= 5; // ADVANCED = 5, ADVANCED_CUSTOM = 6, ADVANCED_SCHEDULED = 7
    }

    /**
     * Check if feature is coming soon (announced but not yet available).
     *
     * @param tenantId The tenant identifier
     * @param featureCode The feature code
     * @return true if access level is COMING_SOON
     */
    public boolean isComingSoon(String tenantId, String featureCode) {
        return "COMING_SOON".equals(getAccessLevel(tenantId, featureCode));
    }

    /**
     * Helper method to get access level value by code.
     */
    private int getAccessLevelValueByCode(String levelCode) {
        return switch (levelCode.toUpperCase()) {
            case "NO" -> 0;
            case "COMING_SOON" -> 1;
            case "LIMITED" -> 2;
            case "BASIC" -> 3;
            case "YES" -> 4;
            case "ADVANCED" -> 5;
            case "ADVANCED_CUSTOM" -> 6;
            case "ADVANCED_SCHEDULED" -> 7;
            default -> 0;
        };
    }
}
