package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.Address;
import lombok.Data;

@Data
public class CreateTenantRequest {
    private String tenantName;
    private String email;
    private String domain;
    private String region;
    private String phoneNo;
    private String tenantType;
    private String industry;
    private String billingCycleType;

    private Long packageId;           // Package ID from IAM (optional, defaults to STARTER in IAM)
    private Long billingCycleId;      // Billing cycle ID from IAM (optional)
    private Boolean startTrial;       // Start with trial period (optional, default false)
    private Address temporaryAddress;
    private Address permanentAddress;
    private Address billingAddress;
    private String adminFirstName;
    private String adminLastName;
    private String adminUserName;
    private String adminPhoneNumber;
    private String adminEmail;
    private String status;

    private String ssoType;   // KEYCLOAK / OKTA / AUTH0 / CUSTOM etc.

    /**
     * Whether this MSSP/Master MSSP tenant also acts as its own Enterprise.
     * When true, the admin gets dual roles (MSSP ADMIN + ENTERPRISE ADMIN)
     * and a default events group is created for their own users.
     * Only applicable for MSSP and MASTER_MSSP tenant types.
     */
    private Boolean selfManaged = false;
}
