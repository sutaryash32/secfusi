package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_tenant_user", columnList = "fk_tenant_id, fk_user_id"),
        @Index(name = "idx_notifications_type", columnList = "type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_notification_id", length = 36)
    private String pkNotificationId;

    @Column(name = "fk_tenant_id", nullable = false, length = 36)
    private String fkTenantId;

    @Column(name = "fk_user_id", nullable = false, length = 36)
    private String fkUserId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private String severity = "INFO";

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService;

    @Column(name = "source_entity_id", length = 36)
    private String sourceEntityId;

    @Column(name = "source_entity_type", length = 50)
    private String sourceEntityType;

    @Column(name = "channels", length = 100)
    private String channels;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "read_at")
    private Instant readAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
