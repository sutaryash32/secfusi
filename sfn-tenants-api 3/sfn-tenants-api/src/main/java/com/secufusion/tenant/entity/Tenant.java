package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Tenant", indexes = {
        @Index(name = "idx_tenant_name", columnList = "tenant_name", unique = true),
        @Index(name = "idx_tenant_domain", columnList = "domain", unique = true),
        @Index(name = "idx_tenant_parent", columnList = "parent_tenant_id"),
        @Index(name = "idx_tenant_status", columnList = "status"),
        @Index(name = "idx_tenant_email", columnList = "email"),
        @Index(name = "idx_tenant_realm", columnList = "realm_name")
})
@Data
@ToString(exclude = {"users", "authProviderConfig"})
@EqualsAndHashCode(exclude = {"users", "authProviderConfig"})
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tenantid")
    private String tenantID;

    @Column(name = "tenant_name")
    private String tenantName;
    private String domain;
    private String email;
    private String region;
    private String phoneNo;
    private String tenantType;
    private String industry;
    @OneToOne(cascade = CascadeType.ALL)
    private Address temporaryAddress;
    @OneToOne(cascade = CascadeType.ALL)
    private Address permanentAddress;
    @OneToOne(cascade = CascadeType.ALL)
    private Address billingAddress;

    private String packageType;
    private String billingCycleType;
    private String features;
    private String loginUrl;

//    @OneToOne(cascade = CascadeType.ALL)
//    @JoinColumn(name = "admin_user_id")
//    private User tenantAdminUser;

    private String status;
    private String realmName;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    private String parentTenantId;

    private String azureTenantId;

    /**
     * Unique tenant code for MSI deployment identification.
     * Used by browser extension to identify tenant without requiring login.
     * Example: "ACME-2024", "CORP-MSI"
     */
    @Column(name = "tenant_code", unique = true, length = 20)
    private String tenantCode;

    /**
     * SSO type requested during tenant creation (KEYCLOAK, AZURE, APIKEY).
     * Persisted so retry/repair flows know the original SSO type even before
     * auth_provider_config is created.
     */
    @Column(name = "sso_type")
    private String ssoType;

    /**
     * Number of times provisioning has been retried after failure.
     * Used by the retry scheduler to stop after MAX_RETRIES.
     */
    @Column(name = "provision_retry_count")
    private int provisionRetryCount = 0;

    /**
     * Last provisioning error message. Stored for debugging when status is FAILED.
     */
    @Column(name = "provision_error", length = 1000)
    private String provisionError;

    /**
     * Bitmask tracking which provisioning steps have completed.
     * Each bit represents one step — allows fine-grained resume on failure.
     * See {@code ProvisionStep} for bit definitions.
     */
    @Column(name = "provision_steps_completed", nullable = false)
    private int provisionStepsCompleted = 0;

    /**
     * Whether this MSSP/Master MSSP tenant also manages its own users directly
     * (acts as both a manager of sub-tenants AND an enterprise with own users/groups/policies).
     * Only relevant for MSSP and MASTER_MSSP tenant types.
     * When true: admin gets both MSSP ADMIN + ENTERPRISE ADMIN roles,
     * a default events group is created, and the UI shows a mode switcher.
     */
    @Column(name = "self_managed", nullable = false)
    private Boolean selfManaged = false;

    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private AuthProviderConfig authProviderConfig;

    /** One Tenant → Many Users */
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<User> users;


}