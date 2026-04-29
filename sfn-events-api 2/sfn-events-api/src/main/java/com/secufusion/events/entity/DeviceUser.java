package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Represents an end user who uses the browser extension.
 * This entity tracks device users across multiple devices.
 *
 * Auto-created by database triggers when devices/events are inserted.
 */
@Entity
@Table(name = "device_user", indexes = {
        @Index(name = "idx_device_user_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_device_user_email", columnList = "email"),
        @Index(name = "idx_device_user_status", columnList = "status"),
        @Index(name = "idx_device_user_portal", columnList = "fk_portal_user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_user_tenant_email", columnNames = {"fk_tenant_id", "email"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceUser {

    @Id
    @UuidGenerator
    @Column(name = "pk_device_user_id", nullable = false, updatable = false, length = 36)
    private String pkDeviceUserId;

    @Column(name = "fk_tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /**
     * Link to portal user (users table) if this device user
     * also has a portal account with the same email.
     */
    @Column(name = "fk_portal_user_id", length = 36)
    private String portalUserId;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private String source;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
