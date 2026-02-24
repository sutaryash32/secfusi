package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ExtensionPolicy Entity
 *
 * Represents browser extension security policies that can be assigned to groups.
 * These policies control which extensions are allowed, blocked, or required.
 */

@Entity
@Table(name = "extension_policy")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_extension_policy_id", length = 36)
    private String pkExtensionPolicyId;

    @Column(name = "fk_tenant_id")
    private String fkTenantId;

    private String name;
    private String description;

    @Column(name = "policy_key")
    private String policyKey;

    private String version;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "landing_page_url", length = 500)
    private String landingPageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
