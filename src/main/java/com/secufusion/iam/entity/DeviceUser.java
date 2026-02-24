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
    name = "device_users",
    indexes = {
        @Index(name = "idx_device_user_tenant", columnList = "tenant_id"),
        @Index(name = "idx_device_user_email", columnList = "email"),
        @Index(name = "idx_device_user_tenant_email", columnList = "tenant_id, email")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUser {

    @Id
    @Column(name = "pk_device_user_id", length = 36, nullable = false)
    private String pkDeviceUserId;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "source", length = 50)
    private String source; // APIKEY, AZURE, etc.

    @Column(name = "azure_user_id", length = 255)
    private String azureUserId; // Azure AD OID for Azure users

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Foreign key relationship to Tenant
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", referencedColumnName = "tenantid", insertable = false, updatable = false)
    private Tenant tenant;
}
