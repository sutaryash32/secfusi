package com.secufusion.iam.dto;

import com.secufusion.iam.entity.Address;
import lombok.Data;

@Data
public class CreateTenantRequest {
    private String tenantName;

//    @NotBlank
//    private String realmName;

    private String email;
    private String domain;
    private String region;
    private String phoneNo;
    private String tenantType;
    private String industry;
    private String billingCycleType;

    private Address temporaryAddress;
    private Address permanentAddress;
    private Address billingAddress;

    // Admin user information
//    @NotBlank
//    private String adminFullName;

    private String adminFirstName;
    private String adminLastName;
    private String adminUserName;
    private String adminPhoneNumber;
    private String adminEmail;
    private String adminPassword;
}
