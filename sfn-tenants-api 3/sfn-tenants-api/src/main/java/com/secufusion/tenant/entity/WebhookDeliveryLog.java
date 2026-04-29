package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_delivery_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_delivery_log_id", length = 36)
    private String pkDeliveryLogId;

    @Column(name = "fk_webhook_config_id", nullable = false, length = 36)
    private String fkWebhookConfigId;

    @Column(name = "fk_tenant_id", nullable = false)
    private String fkTenantId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // SUCCESS, FAILED, RETRYING

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 1;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
