package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.PackagePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackagePricingRepository extends JpaRepository<PackagePricing, Long> {

    @Query("SELECT pp FROM PackagePricing pp WHERE pp.pkg.pkPackageId = :packageId AND pp.isActive = true")
    List<PackagePricing> findByPackageIdAndActive(@Param("packageId") Long packageId);

    @Query("SELECT pp FROM PackagePricing pp WHERE pp.pkg.pkPackageId = :packageId AND pp.billingCycle.pkBillingCycleId = :billingCycleId")
    Optional<PackagePricing> findByPackageIdAndBillingCycleId(
            @Param("packageId") Long packageId,
            @Param("billingCycleId") Long billingCycleId);

    @Query("SELECT pp FROM PackagePricing pp WHERE pp.pkg.pkPackageId = :packageId AND pp.billingCycle.cycleCode = :cycleCode")
    Optional<PackagePricing> findByPackageIdAndBillingCycleCode(
            @Param("packageId") Long packageId,
            @Param("cycleCode") String cycleCode);

    List<PackagePricing> findByIsActiveTrue();
}
