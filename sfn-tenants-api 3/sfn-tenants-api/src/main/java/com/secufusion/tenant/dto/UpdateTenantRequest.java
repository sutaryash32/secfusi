package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.Address;
import lombok.Data;

/**
 * DTO for tenant update operations.
 *
 * Mutable fields:
 * - phone, region, industry, addresses, billingCycleType, status
 * - packageId, billingCycleId (for subscription upgrade)
 * - ssoType (KEYCLOAK / AZURE) — triggers SSO reconfiguration when changed to AZURE
 * - admin user fields (firstName, lastName, phoneNumber) — synced to Keycloak and DB
 * - azure SSO credentials (clientId, clientSecret) — only used when ssoType=AZURE
 *
 * Immutable fields (not included):
 * - tenantName, realmName, domain, email, adminEmail, adminUserName
 */
@Data
public class UpdateTenantRequest {

    // Mutable tenant metadata
    private String phoneNo;
    private String region;
    private String industry;
    private String billingCycleType;
    private String status;  // ACTIVE or INACTIVE

    // Addresses
    private Address temporaryAddress;
    private Address permanentAddress;
    private Address billingAddress;

    // Subscription/Package update fields
    private Long packageId;
    private Long billingCycleId;
    private Boolean startTrial;
    private String azureTenantId;

    // SSO update — set to "AZURE" to enable Hub-and-Spoke Azure SSO
    private String ssoType;

    /**
     * Enable/disable own-org management for MSSP/Master MSSP tenants.
     * true  → admin gets ENTERPRISE ADMIN role + default events group created
     * false → ENTERPRISE ADMIN role removed (groups/policies untouched)
     * null  → no change (field not in request)
     */
    private Boolean selfManaged;

    // Admin user update (DB + Keycloak)
    private String adminFirstName;
    private String adminLastName;
    private String adminPhoneNumber;
}
