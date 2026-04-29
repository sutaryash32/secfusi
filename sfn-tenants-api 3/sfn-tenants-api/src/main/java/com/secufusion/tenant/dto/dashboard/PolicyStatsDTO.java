package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Policy statistics - visible to all tenant types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStatsDTO {

    // Browser policies
    private long totalBrowserPolicies;
    private long activeBrowserPolicies;
    private long inactiveBrowserPolicies;

    // User groups
    private long totalUserGroups;
    private long userGroupsWithPolicies;
    private long userGroupsWithoutPolicies;

    // Policy assignments
    private long usersWithPolicies;
    private long usersWithoutPolicies;
    private long groupsWithMultiplePolicies;

    // Policy changes (this week)
    private long policiesCreatedThisWeek;
    private long policiesModifiedThisWeek;
    private long policiesDeletedThisWeek;

    // Policy distribution by type
    private Map<String, Long> policiesByType;

    // For hierarchy views
    private long policiesAcrossAllTenants;

    public static PolicyStatsDTO empty() {
        return PolicyStatsDTO.builder()
                .totalBrowserPolicies(0)
                .activeBrowserPolicies(0)
                .inactiveBrowserPolicies(0)
                .totalUserGroups(0)
                .userGroupsWithPolicies(0)
                .userGroupsWithoutPolicies(0)
                .usersWithPolicies(0)
                .usersWithoutPolicies(0)
                .groupsWithMultiplePolicies(0)
                .policiesCreatedThisWeek(0)
                .policiesModifiedThisWeek(0)
                .policiesDeletedThisWeek(0)
                .build();
    }
}
