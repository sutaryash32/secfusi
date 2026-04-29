package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * EventsGroup Entity
 *
 * Unified table for both API Key groups and Azure AD groups.
 * Supports two types of groups:
 * - APIKEY_GROUP: Manually created groups for API key users
 * - AZURE_GROUP: Groups synced from Azure AD via Microsoft Graph API
 *
 * Key Features:
 * - Tenant-scoped security boundary
 * - Authorization workflow (admin must authorize groups for policy assignments)
 * - Soft delete support via is_active flag
 * - Unique group names within tenant
 * - Azure AD synchronization support
 */
@Entity
@Table(
    name = "events_groups",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_events_group_tenant_name", columnNames = {"tenant_id", "name"}),
        @UniqueConstraint(name = "uk_events_group_tenant_azure_id", columnNames = {"tenant_id", "azure_group_id"})
    },
    indexes = {
        @Index(name = "idx_events_group_tenant", columnList = "tenant_id"),
        @Index(name = "idx_events_group_default", columnList = "tenant_id, is_default"),
        @Index(name = "idx_events_group_active", columnList = "tenant_id, is_active"),
        @Index(name = "idx_events_group_type", columnList = "tenant_id, group_type"),
        @Index(name = "idx_events_group_authorized", columnList = "tenant_id, authorized"),
        @Index(name = "idx_events_group_azure_id", columnList = "azure_group_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventsGroup {

    @Id
    @Column(name = "pk_events_group_id", length = 36, nullable = false)
    private String pkEventsGroupId;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Group type: APIKEY_GROUP or AZURE_GROUP
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", length = 20, nullable = false)
    private GroupType groupType = GroupType.APIKEY_GROUP;

    /**
     * Whether group is authorized for policy assignments and main app login.
     * For AZURE_GROUP: also controls Keycloak essential claim (filteredByClaims) gate.
     */
    @Column(name = "authorized", nullable = false)
    private Boolean authorized = true; // APIKEY_GROUP defaults to true, AZURE_GROUP defaults to false

    /**
     * Whether group is authorized for browser extension login.
     * Separate gate from main app login (authorized field).
     * Automatically set to true when a group is authorized for main login.
     * Admin can revoke extension access independently without affecting main login.
     * Only applies to AZURE_GROUP type.
     */
    @Column(name = "extension_authorized", nullable = false)
    private Boolean extensionAuthorized = false;

    /**
     * Azure AD group object ID (OID) - only for AZURE_GROUP type
     */
    @Column(name = "azure_group_id", length = 255)
    private String azureGroupId;

    /**
     * Display name from Azure AD - only for AZURE_GROUP type
     */
    @Column(name = "azure_group_display_name", length = 255)
    private String azureGroupDisplayName;

    /**
     * Last sync timestamp from Azure AD - only for AZURE_GROUP type
     */
    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

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
     * Cascade delete ensures groups are removed when tenant is deleted
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", referencedColumnName = "tenantid", insertable = false, updatable = false)
    private Tenant tenant;

    /**
     * Group Type Enum
     */
    public enum GroupType {
        /**
         * Manually created group for API key users
         */
        APIKEY_GROUP,

        /**
         * Group synced from Azure AD
         */
        AZURE_GROUP
    }
}
