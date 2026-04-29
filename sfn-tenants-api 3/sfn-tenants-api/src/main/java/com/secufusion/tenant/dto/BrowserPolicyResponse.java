package com.secufusion.tenant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrowserPolicyResponse {

    private String id;

    private String name;
    private String description;
    private String policyType;
    private JsonNode urlRestriction;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private JsonNode dlp;              // full DLP JSON (optional, simple view)
    private ComplianceRulesResponse complianceRules;
    private HomepageResponse homepage;
    private LandingPageResponse landingPage;
}
