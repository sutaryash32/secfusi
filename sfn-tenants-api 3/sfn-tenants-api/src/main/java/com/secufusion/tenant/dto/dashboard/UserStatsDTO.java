package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User statistics - visible to all tenant types.
 *
 * DATA WINDOWS:
 *   totalUsers, activeUsers, inactiveUsers, lockedUsers,
 *   pendingVerification, mfaEnabledUsers, adminUsers, regularUsers
 *     → ALL-TIME counts (no date filter — reflects current state of the user table).
 *
 *   usersAcrossAllTenants
 *     → ALL-TIME count aggregated across the tenant hierarchy:
 *       PLATFORM_ADMIN : all users in the system
 *       MASTER_MSSP    : own users + child MSSPs' users + grandchild enterprises' users
 *       MSSP           : own users + child enterprises' users
 *       ENTERPRISE     : 0 (not populated — use totalUsers instead)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDTO {

    /** Current all-time count of users in this tenant's own realm. */
    private long totalUsers;
    /** Users with status = ACTIVE. */
    private long activeUsers;
    /** Users with status = INACTIVE. */
    private long inactiveUsers;
    /** Users with status = LOCKED (permanently locked). */
    private long lockedUsers;
    /** Users with status = PENDING (awaiting email/phone verification). */
    private long pendingVerification;
    /** Users who used MFA in the last 30 days (from login audit). */
    private long mfaEnabledUsers;
    /** Users flagged as administrators (TODO: populated when admin flag available). */
    private long adminUsers;
    /** Non-admin users (totalUsers - adminUsers). */
    private long regularUsers;

    /**
     * Total users aggregated across the calling tenant's full hierarchy tree.
     * Populated for PLATFORM_ADMIN, MASTER_MSSP, and MSSP; 0 for ENTERPRISE.
     */
    private long usersAcrossAllTenants;

    public static UserStatsDTO forSingleTenant(long totalUsers, long activeUsers, long inactiveUsers,
            long lockedUsers, long pendingVerification, long mfaEnabledUsers, long adminUsers, long regularUsers) {
        return UserStatsDTO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .lockedUsers(lockedUsers)
                .pendingVerification(pendingVerification)
                .mfaEnabledUsers(mfaEnabledUsers)
                .adminUsers(adminUsers)
                .regularUsers(regularUsers)
                .build();
    }

    public static UserStatsDTO forHierarchy(long totalUsers, long activeUsers, long inactiveUsers,
            long lockedUsers, long pendingVerification, long mfaEnabledUsers, long adminUsers,
            long regularUsers, long usersAcrossAllTenants) {
        return UserStatsDTO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .lockedUsers(lockedUsers)
                .pendingVerification(pendingVerification)
                .mfaEnabledUsers(mfaEnabledUsers)
                .adminUsers(adminUsers)
                .regularUsers(regularUsers)
                .usersAcrossAllTenants(usersAcrossAllTenants)
                .build();
    }
}
