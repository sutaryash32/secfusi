package com.secufusion.iam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    private String parentTenantId;

    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private AuthProviderConfig authProviderConfig;

    /** One Tenant → Many Users */
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<User> users;


}
