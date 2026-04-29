package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * NetworkPolicy Entity
 *
 * Represents network-level security policies that can be assigned to groups.
 * These policies control network access, firewall rules, and connection restrictions.
 */

@Entity
@Table(name = "networkpolicy")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class NetworkPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_networkpolicy_id", length = 36)
    private String pkNetworkPolicyId;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean isEnabled;

    @Column(name = "fk_tenant_id", nullable = false, length = 36)
    private String fkTenantId;

    @Column(length = 36)
    private String policyKey;   // logical policy id (same across versions)

    @Column(length = 10)
    private String version;     // 0.1, 0.2, 1.0

    @Column
    private boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
