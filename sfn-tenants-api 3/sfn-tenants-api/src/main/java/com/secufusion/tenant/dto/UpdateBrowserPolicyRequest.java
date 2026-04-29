package com.secufusion.tenant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateBrowserPolicyRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String policyType;

    private JsonNode urlRestriction;

    private DlpRequest dlp;
    private ComplianceRulesRequest complianceRules;
    private HomepageRequest homepage;
}
