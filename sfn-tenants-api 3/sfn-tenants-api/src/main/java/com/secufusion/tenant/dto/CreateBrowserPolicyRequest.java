package com.secufusion.tenant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.secufusion.tenant.dto.ComplianceRulesRequest;
import com.secufusion.tenant.dto.DlpRequest;
import com.secufusion.tenant.dto.HomepageRequest;
import com.secufusion.tenant.dto.LandingPageRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBrowserPolicyRequest {

    @NotBlank
    private String name;

    private String description;

    // matches "policy_type"
    @NotBlank
    private String policyType;

    // whole "url_restriction" object
    private JsonNode urlRestriction;

    // sections
    private DlpRequest dlp;
    private ComplianceRulesRequest complianceRules;
    private HomepageRequest homepage;
    private LandingPageRequest landingPage;
}
