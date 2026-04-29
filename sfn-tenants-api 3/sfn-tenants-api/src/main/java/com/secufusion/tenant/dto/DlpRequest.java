package com.secufusion.tenant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class DlpRequest {

    private boolean disableCopy;
    private boolean disablePaste;
    private boolean disableDownload;
    private boolean blockPrinting;

    // nested structures as JSON
    private JsonNode clipboard;
    private JsonNode piiDetection;
    private JsonNode fileOperations;
    private JsonNode formControls;
    private JsonNode communicationPlatforms;
    private JsonNode securityPolicies;
    private JsonNode behaviorMonitoring;
    private JsonNode policyEnforcement;

    private WatermarkingRequest watermarking;
}
