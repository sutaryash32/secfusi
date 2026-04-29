package com.secufusion.tenant.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateWebhookConfigRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Webhook type is required (SLACK, TEAMS, PAGERDUTY, CUSTOM)")
    private String webhookType;

    @NotBlank(message = "URL is required")
    private String url;

    private Map<String, String> headers;

    private String secret;

    private List<String> categories;

    private List<String> severities;
}
