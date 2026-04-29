package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_addon_feature",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenant_addon_feature",
                columnNames = {"fk_tenant_id", "fk_feature_id"}
        ))
public class TenantAddonFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_addon_id")
    private Long pkAddonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_feature_id", nullable = false)
    private Feature feature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_access_level_id", nullable = false)
    private AccessLevel accessLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_retention_period_id")
    private RetentionPeriod retentionPeriod;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_trial")
    private Boolean isTrial = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_config", columnDefinition = "jsonb")
    private Map<String, Object> customConfig;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
