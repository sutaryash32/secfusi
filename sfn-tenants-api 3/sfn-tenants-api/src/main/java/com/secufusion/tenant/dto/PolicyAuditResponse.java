package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class PolicyAuditResponse {

    private String policyId;
    private String policyKey;
    private String version;
    private boolean active;

    private Number revision;
    private Instant revisionTimestamp;
    private String revisionType; // ADD / MOD / DEL
}
