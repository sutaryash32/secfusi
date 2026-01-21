package com.secufusion.iam.repository;

import com.secufusion.iam.entity.TenantAddonFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantAddonFeatureRepository extends JpaRepository<TenantAddonFeature, Long> {

    List<TenantAddonFeature> findByTenantTenantID(String tenantId);

    List<TenantAddonFeature> findByTenantTenantIDAndIsEnabledTrue(String tenantId);

    Optional<TenantAddonFeature> findByTenantTenantIDAndFeaturePkFeatureID(String tenantId, Long featureId);

    Optional<TenantAddonFeature> findByTenantTenantIDAndFeatureFeatureCode(String tenantId, String featureCode);

    @Query("SELECT taf FROM TenantAddonFeature taf " +
            "WHERE taf.tenant.tenantID = :tenantId " +
            "AND taf.feature.featureCode = :featureCode " +
            "AND taf.isEnabled = true " +
            "AND (taf.startDate IS NULL OR taf.startDate <= :currentDate) " +
            "AND (taf.endDate IS NULL OR taf.endDate >= :currentDate)")
    Optional<TenantAddonFeature> findActiveAddonByTenantAndFeatureCode(
            @Param("tenantId") String tenantId,
            @Param("featureCode") String featureCode,
            @Param("currentDate") LocalDate currentDate);

    @Query("SELECT taf FROM TenantAddonFeature taf " +
            "WHERE taf.tenant.tenantID = :tenantId " +
            "AND taf.isEnabled = true " +
            "AND (taf.startDate IS NULL OR taf.startDate <= :currentDate) " +
            "AND (taf.endDate IS NULL OR taf.endDate >= :currentDate)")
    List<TenantAddonFeature> findAllActiveAddonsByTenant(
            @Param("tenantId") String tenantId,
            @Param("currentDate") LocalDate currentDate);

    boolean existsByTenantTenantIDAndFeaturePkFeatureID(String tenantId, Long featureId);

    boolean existsByTenantTenantIDAndFeatureFeatureCode(String tenantId, String featureCode);

    void deleteByTenantTenantIDAndFeaturePkFeatureID(String tenantId, Long featureId);
}
