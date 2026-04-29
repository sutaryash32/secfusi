package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notification_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_preference_id", length = 36)
    private String pkPreferenceId;

    @Column(name = "fk_tenant_id", nullable = false, length = 36)
    private String fkTenantId;

    @Column(name = "fk_user_id", nullable = false, length = 36)
    private String fkUserId;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "websocket_enabled", nullable = false)
    @Builder.Default
    private boolean websocketEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private boolean inAppEnabled = true;

    @Column(name = "webhook_enabled", nullable = false)
    @Builder.Default
    private boolean webhookEnabled = true;

    @Column(name = "min_severity", length = 20)
    private String minSeverity;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
