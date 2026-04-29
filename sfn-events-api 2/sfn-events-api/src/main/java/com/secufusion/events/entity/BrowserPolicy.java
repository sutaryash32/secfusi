package com.secufusion.events.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * BrowserPolicy Entity
 *
 * Represents browser-level security policies that can be assigned to groups.
 * These policies control browser behavior, security settings, and access controls.
 */

@Entity
@Table(name = "browserpolicy")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrowserPolicy {

    /* ===================== Primary Key ===================== */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_browserpolicy_id", length = 36)
    private String pkBrowserPolicyId;

    /* ===================== Basic Fields ===================== */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 250)
    private String description;

    @Column(length = 250)
    private String deviceLimit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "url_restriction", columnDefinition = "jsonb")
    private JsonNode urlRestriction;

    @Column(name = "policy_type", length = 100)
    private String policyType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private String fkTenantId;

    @Column(length = 36)
    private String policyKey;

    @Column(length = 10)
    private String version;

    @Column
    private boolean isActive = true;

    /**
     * Landing Page URL - Direct URL entered by user.
     * Used when landingPageId is null.
     */
    @Column(name = "landing_page_url", length = 500)
    private String landingPageUrl;

    /**
     * Reference to an existing LandingPage by ID.
     * Used when user creates/selects a custom landing page with shortcuts.
     */
    @Column(name = "fk_landingpage_id", length = 36)
    private String landingPageId;

    /* ===================== Lifecycle Hooks ===================== */
    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}