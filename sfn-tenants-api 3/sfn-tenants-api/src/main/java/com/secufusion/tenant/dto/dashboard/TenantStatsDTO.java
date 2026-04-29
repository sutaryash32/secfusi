package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant statistics - visible to MASTER_MSSP and MSSP only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatsDTO {

    private long totalMssps;           // Only for MASTER_MSSP
    private long activeMssps;          // Only for MASTER_MSSP
    private long totalEnterprises;     // For MASTER_MSSP and MSSP
    private long activeEnterprises;    // For MASTER_MSSP and MSSP
    private long pendingEnterprises;   // Enterprises awaiting activation
    private long suspendedTenants;     // Suspended tenants count

    public static TenantStatsDTO forPlatformAdmin(long totalMssps, long activeMssps,
            long totalEnterprises, long activeEnterprises, long pendingEnterprises, long suspendedTenants) {
        return TenantStatsDTO.builder()
                .totalMssps(totalMssps)
                .activeMssps(activeMssps)
                .totalEnterprises(totalEnterprises)
                .activeEnterprises(activeEnterprises)
                .pendingEnterprises(pendingEnterprises)
                .suspendedTenants(suspendedTenants)
                .build();
    }

    public static TenantStatsDTO forMasterMssp(long totalMssps, long activeMssps,
            long totalEnterprises, long activeEnterprises, long pendingEnterprises, long suspendedTenants) {
        return TenantStatsDTO.builder()
                .totalMssps(totalMssps)
                .activeMssps(activeMssps)
                .totalEnterprises(totalEnterprises)
                .activeEnterprises(activeEnterprises)
                .pendingEnterprises(pendingEnterprises)
                .suspendedTenants(suspendedTenants)
                .build();
    }

    public static TenantStatsDTO forMssp(long totalEnterprises, long activeEnterprises,
            long pendingEnterprises, long suspendedTenants) {
        return TenantStatsDTO.builder()
                .totalEnterprises(totalEnterprises)
                .activeEnterprises(activeEnterprises)
                .pendingEnterprises(pendingEnterprises)
                .suspendedTenants(suspendedTenants)
                .build();
    }
}
