package com.secufusion.tenant.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.secufusion.tenant.entity.Address;
import lombok.Data;

import java.time.Instant;

@Data
public class TenantResponse {
    private String tenantID;
    private String tenantName;
    private String realmName;
    private String domain;
    private String region;
    private String phoneNo;
    private String tenantType;
    private String industry;
    @JsonIgnore
    private Address temporaryAddress;
    @JsonIgnore
    private Address permanentAddress;
    @JsonIgnore
    private Address billingAddress;
    private String status;
    private Instant createdAt;
    private String loginUrl;
    private String apiKey;    // Optional: Include API key info if needed

    // Subscription info from IAM API
    private SubscriptionSummary subscription;
}
