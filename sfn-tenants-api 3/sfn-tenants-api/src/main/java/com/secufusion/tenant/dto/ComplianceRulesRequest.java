package com.secufusion.tenant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ComplianceRulesRequest {

    @JsonProperty("anti_virus_check")
    private boolean antivirusCheck;

    @JsonProperty("disk_encryption_check")
    private boolean diskEncryptionCheck;

    @JsonProperty("firewall_check")
    private boolean firewallCheck;

    private boolean geolocation;
}
