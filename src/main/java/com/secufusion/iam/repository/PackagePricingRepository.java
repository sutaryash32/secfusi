package com.secufusion.iam.repository;

import com.secufusion.iam.entity.PackagePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackagePricingRepository extends JpaRepository<PackagePricing, Long> {

    /**
     * Find pricing by package ID and billing cycle ID.
     */
    @Query("SELECT pp FROM PackagePricing pp " +
            "WHERE pp.pkg.pkPackageId = :packageId " +
            "AND pp.billingCycle.pkBillingCycleId = :billingCycleId " +
            "AND pp.isActive = true")
    Optional<PackagePricing> findByPackageAndBillingCycle(
            @Param("packageId") Long packageId,
            @Param("billingCycleId") Long billingCycleId);

    /**
     * Find pricing by package ID and billing cycle code.
     */
    @Query("SELECT pp FROM PackagePricing pp " +
            "JOIN FETCH pp.billingCycle bc " +
            "WHERE pp.pkg.pkPackageId = :packageId " +
            "AND bc.cycleCode = :cycleCode " +
            "AND pp.isActive = true")
    Optional<PackagePricing> findByPackageIdAndCycleCode(
            @Param("packageId") Long packageId,
            @Param("cycleCode") String cycleCode);

    /**
     * Find all pricing for a package.
     */
    @Query("SELECT pp FROM PackagePricing pp " +
            "JOIN FETCH pp.billingCycle bc " +
            "WHERE pp.pkg.pkPackageId = :packageId " +
            "AND pp.isActive = true " +
            "ORDER BY bc.durationMonths")
    List<PackagePricing> findAllByPackageId(@Param("packageId") Long packageId);

    /**
     * Find all pricing for a billing cycle.
     */
    @Query("SELECT pp FROM PackagePricing pp " +
            "JOIN FETCH pp.pkg p " +
            "WHERE pp.billingCycle.pkBillingCycleId = :billingCycleId " +
            "AND pp.isActive = true " +
            "ORDER BY pp.finalPrice")
    List<PackagePricing> findAllByBillingCycleId(@Param("billingCycleId") Long billingCycleId);

    /**
     * Find all active pricing with details.
     */
    @Query("SELECT pp FROM PackagePricing pp " +
            "JOIN FETCH pp.pkg p " +
            "JOIN FETCH pp.billingCycle bc " +
            "WHERE pp.isActive = true " +
            "ORDER BY p.packageName, bc.durationMonths")
    List<PackagePricing> findAllActiveWithDetails();

    /**
     * Find pricing by package name and billing cycle code.
     */
    @Query("SELECT pp FROM PackagePricing pp " +
            "JOIN FETCH pp.pkg p " +
            "JOIN FETCH pp.billingCycle bc " +
            "WHERE p.packageName = :packageName " +
            "AND bc.cycleCode = :cycleCode " +
            "AND pp.isActive = true")
    Optional<PackagePricing> findByPackageNameAndCycleCode(
            @Param("packageName") String packageName,
            @Param("cycleCode") String cycleCode);
}
