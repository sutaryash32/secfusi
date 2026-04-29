package com.secufusion.iam.repository;

import com.secufusion.iam.entity.AddonPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddonPricingRepository extends JpaRepository<AddonPricing, Long> {

    /**
     * Find pricing for a feature with a specific billing cycle.
     */
    @Query("SELECT ap FROM AddonPricing ap " +
            "WHERE ap.feature.pkFeatureID = :featureId " +
            "AND ap.billingCycle.pkBillingCycleId = :billingCycleId " +
            "AND ap.isActive = true")
    Optional<AddonPricing> findByFeatureAndBillingCycle(
            @Param("featureId") Long featureId,
            @Param("billingCycleId") Long billingCycleId);

    /**
     * Find pricing by feature code and billing cycle code.
     */
    @Query("SELECT ap FROM AddonPricing ap " +
            "WHERE ap.feature.featureCode = :featureCode " +
            "AND ap.billingCycle.cycleCode = :billingCycleCode " +
            "AND ap.isActive = true")
    Optional<AddonPricing> findByFeatureCodeAndBillingCycleCode(
            @Param("featureCode") String featureCode,
            @Param("billingCycleCode") String billingCycleCode);

    /**
     * Find all pricing options for a feature.
     */
    @Query("SELECT ap FROM AddonPricing ap " +
            "JOIN FETCH ap.billingCycle bc " +
            "WHERE ap.feature.pkFeatureID = :featureId " +
            "AND ap.isActive = true " +
            "ORDER BY bc.durationMonths ASC")
    List<AddonPricing> findAllByFeatureId(@Param("featureId") Long featureId);

    /**
     * Find all pricing options for a feature by code.
     */
    @Query("SELECT ap FROM AddonPricing ap " +
            "JOIN FETCH ap.billingCycle bc " +
            "WHERE ap.feature.featureCode = :featureCode " +
            "AND ap.isActive = true " +
            "ORDER BY bc.durationMonths ASC")
    List<AddonPricing> findAllByFeatureCode(@Param("featureCode") String featureCode);

    /**
     * Find all addon features with their pricing.
     */
    @Query("SELECT DISTINCT ap FROM AddonPricing ap " +
            "JOIN FETCH ap.feature f " +
            "JOIN FETCH ap.billingCycle bc " +
            "WHERE f.isAddon = true " +
            "AND f.isActive = true " +
            "AND ap.isActive = true " +
            "ORDER BY f.featureName, bc.durationMonths")
    List<AddonPricing> findAllAddonPricing();

    /**
     * Check if pricing exists for a feature and billing cycle.
     */
    boolean existsByFeaturePkFeatureIDAndBillingCyclePkBillingCycleId(Long featureId, Long billingCycleId);
}
