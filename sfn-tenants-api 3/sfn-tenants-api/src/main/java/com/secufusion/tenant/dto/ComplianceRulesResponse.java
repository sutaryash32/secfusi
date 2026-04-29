package com.secufusion.tenant.dto;

import lombok.Data;

@Data
public class ComplianceRulesResponse {

    private boolean antivirusCheck;
    private boolean diskEncryptionCheck;
    private boolean firewallCheck;
    private boolean geolocation;
}