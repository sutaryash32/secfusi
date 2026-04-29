package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "webhook_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wc_tenant_name", columnNames = {"fk_tenant_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_webhook_config_id", length = 36)
    private String pkWebhookConfigId;

    @Column(name = "fk_tenant_id", nullable = false)
    private String fkTenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_type", nullable = false, length = 20)
    private WebhookType webhookType;

    @Column(name = "url", nullable = false, length = 2000)
    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private java.util.Map<String, String> headers;

    @Column(name = "secret", length = 500)
    private String secret;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "categories", columnDefinition = "jsonb")
    private List<String> categories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "severities", columnDefinition = "jsonb")
    private List<String> severities;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
