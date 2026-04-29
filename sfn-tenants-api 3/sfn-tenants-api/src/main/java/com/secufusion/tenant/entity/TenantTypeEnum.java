package com.secufusion.tenant.entity;

/**
 * Enum representing the tenant hierarchy levels.
 *
 * MASTER_MSSP (Level 1): Platform owner, manages all MSSPs
 * MSSP (Level 2): Managed Security Service Provider, manages multiple Enterprises
 * ENTERPRISE (Level 3): End customer organization
 */
public enum TenantTypeEnum {

    PLATFORM_ADMIN("PLATFORM_ADMIN", 0, "Platform Administrator - Full access to all data without limitations"),
    MASTER_MSSP("MASTER_MSSP", 1, "Platform Owner - Manages all MSSPs"),
    MSSP("MSSP", 2, "Managed Security Service Provider - Manages Enterprises"),
    ENTERPRISE("ENTERPRISE", 3, "Enterprise Customer");

    private final String code;
    private final int level;
    private final String description;

    TenantTypeEnum(String code, int level, String description) {
        this.code = code;
        this.level = level;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this tenant type can manage another tenant type.
     */
    public boolean canManage(TenantTypeEnum other) {
        return this.level < other.level;
    }

    /**
     * Check if this is the top-level tenant (Master MSSP).
     */
    public boolean isMasterMssp() {
        return this == MASTER_MSSP;
    }

    /**
     * Check if this is a Platform Admin (no parent, full access).
     */
    public boolean isPlatformAdmin() {
        return this == PLATFORM_ADMIN;
    }

    /**
     * Check if this is an MSSP.
     */
    public boolean isMssp() {
        return this == MSSP;
    }

    /**
     * Check if this is an Enterprise.
     */
    public boolean isEnterprise() {
        return this == ENTERPRISE;
    }

    /**
     * Get enum from string code.
     */
    public static TenantTypeEnum fromCode(String code) {
        if (code == null) {
            return ENTERPRISE; // Default
        }
        for (TenantTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return ENTERPRISE; // Default fallback
    }
}
