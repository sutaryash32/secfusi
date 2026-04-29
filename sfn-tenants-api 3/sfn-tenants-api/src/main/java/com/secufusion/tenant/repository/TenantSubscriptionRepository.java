package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {

    /**
     * Find active subscription for a tenant.
     */
    @Query("SELECT ts FROM TenantSubscription ts WHERE ts.tenant.tenantID = :tenantId " +
           "AND ts.status IN ('ACTIVE', 'TRIAL', 'GRACE_PERIOD') " +
           "ORDER BY ts.createdAt DESC")
    Optional<TenantSubscription> findActiveByTenantId(@Param("tenantId") String tenantId);

    /**
     * Find all subscriptions for a tenant.
     */
    @Query("SELECT ts FROM TenantSubscription ts WHERE ts.tenant.tenantID = :tenantId ORDER BY ts.createdAt DESC")
    List<TenantSubscription> findAllByTenantId(@Param("tenantId") String tenantId);

    /**
     * Find subscription by tenant ID and status.
     */
    @Query("SELECT ts FROM TenantSubscription ts WHERE ts.tenant.tenantID = :tenantId AND ts.status = :status")
    Optional<TenantSubscription> findByTenantIdAndStatus(
            @Param("tenantId") String tenantId,
            @Param("status") TenantSubscription.Status status);

    /**
     * Find subscriptions expiring soon.
     */
    @Query("SELECT ts FROM TenantSubscription ts WHERE ts.status IN ('ACTIVE', 'TRIAL') " +
           "AND ts.endDate BETWEEN :startDate AND :endDate")
    List<TenantSubscription> findExpiringSoon(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Find trial subscriptions expiring soon.
     */
    @Query("SELECT ts FROM TenantSubscription ts WHERE ts.status = 'TRIAL' " +
           "AND ts.trialEndDate BETWEEN :startDate AND :endDate")
    List<TenantSubscription> findTrialsExpiringSoon(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Find subscriptions by package.
     */
    @Query("SELECT ts FROM TenantSubscription ts WHERE ts.pkg.pkPackageId = :packageId")
    List<TenantSubscription> findByPackageId(@Param("packageId") Long packageId);

    /**
     * Count active subscriptions by package.
     */
    @Query("SELECT COUNT(ts) FROM TenantSubscription ts WHERE ts.pkg.pkPackageId = :packageId " +
           "AND ts.status IN ('ACTIVE', 'TRIAL', 'GRACE_PERIOD')")
    long countActiveByPackageId(@Param("packageId") Long packageId);

    /**
     * Count subscriptions by status.
     */
    long countByStatus(TenantSubscription.Status status);

    /**
     * Find subscriptions needing renewal reminder.
     */
    @Query("SELECT ts FROM TenantSubscription ts WHERE ts.status = 'ACTIVE' " +
           "AND ts.autoRenew = true AND ts.renewalReminderSent = false " +
           "AND ts.nextBillingDate BETWEEN :startDate AND :endDate")
    List<TenantSubscription> findNeedingRenewalReminder(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Modifying
    @Query("DELETE FROM TenantSubscription ts WHERE ts.tenant.tenantID = :tenantId")
    void deleteByTenantId(@Param("tenantId") String tenantId);
}
