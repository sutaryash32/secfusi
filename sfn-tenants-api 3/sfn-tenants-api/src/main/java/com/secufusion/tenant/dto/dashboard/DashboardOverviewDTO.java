package com.secufusion.tenant.dto.dashboard;

import com.secufusion.tenant.entity.TenantTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Main dashboard overview DTO - adapts based on tenant type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO {

    private TenantTypeEnum tenantType;
    private String tenantId;
    private String tenantName;
    private LocalDateTime generatedAt;

    // Tenant Stats (visible to MASTER_MSSP and MSSP)
    private TenantStatsDTO tenantStats;

    // User Stats (visible to all)
    private UserStatsDTO userStats;

    // Session Stats (visible to all)
    private SessionStatsDTO sessionStats;

    // Login Stats (visible to all)
    private LoginStatsDTO loginStats;

    // Security Stats (visible to all)
    private SecurityStatsDTO securityStats;

    // Policy Stats (visible to all)
    private PolicyStatsDTO policyStats;

    // License Stats (visible to MASTER_MSSP and MSSP)
    private LicenseStatsDTO licenseStats;

    // Device & Browser Event Stats (visible to all)
    private BrowserEventStatsDTO deviceStats;

    /**
     * Whether this tenant is selfManaged (MSSP/Master MSSP that also has own users).
     * When true the UI shows the mode switcher and myOrgStats panel.
     */
    private boolean selfManaged;

    /**
     * Own-org stats for selfManaged MSSP/Master MSSP tenants.
     * Contains user/policy/group stats scoped only to the tenant's own users
     * (not their sub-tenants). Null for non-selfManaged tenants.
     */
    private MyOrgStatsDTO myOrgStats;

    public static DashboardOverviewDTO forPlatformAdmin(String tenantId, String tenantName) {
        return DashboardOverviewDTO.builder()
                .tenantType(TenantTypeEnum.PLATFORM_ADMIN)
                .tenantId(tenantId)
                .tenantName(tenantName)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public static DashboardOverviewDTO forMasterMssp(String tenantId, String tenantName) {
        return DashboardOverviewDTO.builder()
                .tenantType(TenantTypeEnum.MASTER_MSSP)
                .tenantId(tenantId)
                .tenantName(tenantName)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public static DashboardOverviewDTO forMssp(String tenantId, String tenantName) {
        return DashboardOverviewDTO.builder()
                .tenantType(TenantTypeEnum.MSSP)
                .tenantId(tenantId)
                .tenantName(tenantName)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public static DashboardOverviewDTO forEnterprise(String tenantId, String tenantName) {
        return DashboardOverviewDTO.builder()
                .tenantType(TenantTypeEnum.ENTERPRISE)
                .tenantId(tenantId)
                .tenantName(tenantName)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
