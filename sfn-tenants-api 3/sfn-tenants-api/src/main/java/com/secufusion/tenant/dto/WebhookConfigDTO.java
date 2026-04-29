package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookConfigDTO {

    private String webhookConfigId;
    private String name;
    private String webhookType;
    private String url;
    private Map<String, String> headers;
    private boolean hasSecret;
    private List<String> categories;
    private List<String> severities;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}
