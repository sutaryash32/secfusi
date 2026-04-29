package com.secufusion.tenant.dto;

import lombok.Data;

@Data
public class GenerateApiKeyRequest {

    private String tenantId;
    private String createdBy;
}
