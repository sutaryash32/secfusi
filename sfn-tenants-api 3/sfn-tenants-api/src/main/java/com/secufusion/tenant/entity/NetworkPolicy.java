package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "fk_tenant_id", length = 36)
    private String fkTenantId;

    @Column(length = 36)
    private String policyKey;   // logical policy id (same across versions)

    @Column(length = 10)
    private String version;     // 0.1, 0.2, 1.0

    @Column
    private boolean isActive;

    @Column(name = "is_tenant_default", nullable = false)
    private boolean isTenantDefault = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /* ===================== Computed Fields ===================== */

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("globalDefault")
    public boolean isGlobalDefault() {
        return fkTenantId == null;
    }

    /* ===================== Relationships ===================== */

    @OneToMany(mappedBy = "networkPolicy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference("networkPolicy-urlFilters")
    private List<UrlFilter> urlFilters = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_networkconfiguration_id")
    private NetworkConfiguration networkConfiguration;

    /* ===================== Lifecycle ===================== */

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
