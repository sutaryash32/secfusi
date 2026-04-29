package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Own-org stats for selfManaged MSSP/Master MSSP tenants.
 * Scoped only to the tenant's own users, groups and policies —
 * does NOT include sub-tenant data.
 * Rendered in the "My Organization" panel of the mode-switched dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyOrgStatsDTO {

    /** Total users that belong directly to this tenant (own staff). */
    private long ownUserCount;

    /** Active users in this tenant. */
    private long activeUserCount;

    /** Total events groups (APIKEY + AZURE) in this tenant. */
    private long groupCount;

    /** Number of AZURE_GROUP groups that are authorized for login. */
    private long authorizedGroupCount;

    /** Total browser policy assignments for this tenant. */
    private long policyAssignmentCount;

    /** Number of active browser devices registered for this tenant. */
    private long activeDeviceCount;
}
