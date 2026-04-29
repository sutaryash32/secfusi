package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryLogDTO {

    private String deliveryLogId;
    private String webhookConfigId;
    private String eventType;
    private String status;
    private Integer httpStatus;
    private Integer attemptCount;
    private String errorMessage;
    private String createdAt;
}
