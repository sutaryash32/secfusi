package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


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

    @Column(name = "is_tenant_default", nullable = false)
    private boolean isTenantDefault = false;

    /* ===== Reused child policies (same as BrowserPolicy) ===== */

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "fk_dlp_id")
    private Dlp dlp;

    @OneToMany(mappedBy = "extensionPolicy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference("extensionPolicy-urlFilters")
    private List<UrlFilter> urlFilters = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "fk_managed_extensions_id")
    @JsonProperty("managedExtension")
    @JsonAlias("managedExtensions")
    private ManagedExtension managedExtension;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "fk_compliance_rules_id")
    private ComplianceRules complianceRules;

    @Column(name = "landing_page_url", length = 500)
    private String landingPageUrl;

    @Column(name = "fk_landingpage_id", length = 36)
    private String landingPageId;

    /* ===== Computed Fields ===== */

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("globalDefault")
    public boolean isGlobalDefault() {
        return fkTenantId == null;
    }

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

    public void setGlobalDefault(boolean b) {

    }
}
