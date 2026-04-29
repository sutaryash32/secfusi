package com.secufusion.tenant.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "tenant_api_mappings")
public class TenantApiMappingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "api_id", nullable = false)
    private ApiFlagEntity api;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // getters/setters
}

