package com.secufusion.events.entity;

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
@Table(name = "Tenant")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tenantID;

    private String tenantName;
    private String domain;
    private String email;
    private String region;
    private String phoneNo;
    private String tenantType;
    private String industry;

    private String packageType;
    private String billingCycleType;
    private String features;
    private String loginUrl;

//    @OneToOne(cascade = CascadeType.ALL)
//    @JoinColumn(name = "admin_user_id")
//    private User tenantAdminUser;

    private String status;
    private String realmName;

    /**
     * Unique tenant code for MSI deployment identification.
     * E.g., "ACME-2024", "CORP-MSI"
     */
    @Column(name = "tenant_code", unique = true, length = 20)
    private String tenantCode;

    @CreationTimestamp
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    private String parentTenantId;



}
