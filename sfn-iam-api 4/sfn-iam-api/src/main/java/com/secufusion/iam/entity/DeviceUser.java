package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * DeviceUser Entity
 *
 * Represents users who authenticate via API keys or Azure AD
 * and access resources through the events system.
 *
 * Key Features:
 * - Tenant-scoped security boundary
 * - Maps to events groups for policy assignment
 * - Tracks device login information
 */
@Entity
@Table(
    name = "device_user",
    indexes = {
        @Index(name = "idx_device_user_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_device_user_email", columnList = "email"),
        @Index(name = "idx_device_user_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_user_tenant_email", columnNames = {"fk_tenant_id", "email"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUser {

    @Id
    @Column(name = "pk_device_user_id", length = 36, nullable = false)
    private String pkDeviceUserId;

    @Column(name = "fk_tenant_id", length = 50, nullable = false)
    private String fkTenantId;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "fk_portal_user_id", length = 36)
    private String portalUserId;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, etc.

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    private String source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Foreign key relationship to Tenant
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", referencedColumnName = "tenantid", insertable = false, updatable = false)
    private Tenant tenant;
}
