package com.secufusion.iam.dto;

import lombok.Data;
import java.util.Set;

@Data
public class UpdateScopeTenantTypesRequest {
    private Set<String> tenantTypes; // MASTER MSSP, MSSP, ENTERPRISE
}
