package com.secufusion.iam.repository;

import com.secufusion.iam.entity.TenantSubscription;
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
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.pkg p " +
            "JOIN FETCH ts.billingCycle bc " +
            "LEFT JOIN FETCH ts.pricing pr " +
            "WHERE ts.tenant.tenantID = :tenantId " +
            "AND ts.status IN ('ACTIVE', 'TRIAL', 'GRACE_PERIOD')")
    Optional<TenantSubscription> findActiveByTenantId(@Param("tenantId") String tenantId);

    /**
     * Find all subscriptions for a tenant (including history).
     */
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.pkg p " +
            "JOIN FETCH ts.billingCycle bc " +
            "WHERE ts.tenant.tenantID = :tenantId " +
            "ORDER BY ts.createdAt DESC")
    List<TenantSubscription> findAllByTenantId(@Param("tenantId") String tenantId);

    /**
     * Find subscriptions expiring soon (within specified days).
     */
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.tenant t " +
            "JOIN FETCH ts.pkg p " +
            "WHERE ts.status = 'ACTIVE' " +
            "AND ts.endDate IS NOT NULL " +
            "AND ts.endDate BETWEEN :today AND :futureDate " +
            "AND (ts.renewalReminderSent = false OR ts.renewalReminderSent IS NULL)")
    List<TenantSubscription> findExpiringSoon(
            @Param("today") LocalDate today,
            @Param("futureDate") LocalDate futureDate);

    /**
     * Find trials expiring soon (within specified days).
     */
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.tenant t " +
            "JOIN FETCH ts.pkg p " +
            "WHERE ts.status = 'TRIAL' " +
            "AND ts.isTrial = true " +
            "AND ts.trialEndDate BETWEEN :today AND :futureDate")
    List<TenantSubscription> findTrialsExpiringSoon(
            @Param("today") LocalDate today,
            @Param("futureDate") LocalDate futureDate);

    /**
     * Find expired trials that need to be converted or expired.
     */
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.tenant t " +
            "WHERE ts.status = 'TRIAL' " +
            "AND ts.isTrial = true " +
            "AND ts.trialEndDate < :today " +
            "AND ts.trialConverted = false")
    List<TenantSubscription> findExpiredTrials(@Param("today") LocalDate today);

    /**
     * Find subscriptions in grace period that have expired.
     */
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.tenant t " +
            "WHERE ts.status = 'GRACE_PERIOD' " +
            "AND ts.graceEndDate < :today")
    List<TenantSubscription> findExpiredGracePeriods(@Param("today") LocalDate today);

    /**
     * Find all active subscriptions for a package.
     */
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.tenant t " +
            "WHERE ts.pkg.pkPackageId = :packageId " +
            "AND ts.status IN ('ACTIVE', 'TRIAL', 'GRACE_PERIOD')")
    List<TenantSubscription> findActiveByPackageId(@Param("packageId") Long packageId);

    /**
     * Count active subscriptions by package.
     */
    @Query("SELECT ts.pkg.pkPackageId, COUNT(ts) FROM TenantSubscription ts " +
            "WHERE ts.status IN ('ACTIVE', 'TRIAL', 'GRACE_PERIOD') " +
            "GROUP BY ts.pkg.pkPackageId")
    List<Object[]> countActiveByPackage();

    /**
     * Mark subscription as expired.
     */
    @Modifying
    @Query("UPDATE TenantSubscription ts " +
            "SET ts.status = 'EXPIRED', ts.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE ts.pkSubscriptionId = :subscriptionId")
    void markAsExpired(@Param("subscriptionId") Long subscriptionId);

    /**
     * Mark subscription as grace period.
     */
    @Modifying
    @Query("UPDATE TenantSubscription ts " +
            "SET ts.status = 'GRACE_PERIOD', " +
            "ts.graceEndDate = :graceEndDate, " +
            "ts.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE ts.pkSubscriptionId = :subscriptionId")
    void markAsGracePeriod(
            @Param("subscriptionId") Long subscriptionId,
            @Param("graceEndDate") LocalDate graceEndDate);

    /**
     * Mark renewal reminder as sent.
     */
    @Modifying
    @Query("UPDATE TenantSubscription ts " +
            "SET ts.renewalReminderSent = true, ts.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE ts.pkSubscriptionId = :subscriptionId")
    void markRenewalReminderSent(@Param("subscriptionId") Long subscriptionId);

    /**
     * Check if tenant has any active subscription.
     */
    @Query("SELECT COUNT(ts) > 0 FROM TenantSubscription ts " +
            "WHERE ts.tenant.tenantID = :tenantId " +
            "AND ts.status IN ('ACTIVE', 'TRIAL', 'GRACE_PERIOD')")
    boolean hasActiveSubscription(@Param("tenantId") String tenantId);

    /**
     * Find subscriptions needing billing (next_billing_date is today or past).
     */
    @Query("SELECT ts FROM TenantSubscription ts " +
            "JOIN FETCH ts.tenant t " +
            "JOIN FETCH ts.pkg p " +
            "LEFT JOIN FETCH ts.pricing pr " +
            "WHERE ts.status = 'ACTIVE' " +
            "AND ts.autoRenew = true " +
            "AND ts.nextBillingDate <= :today")
    List<TenantSubscription> findNeedingBilling(@Param("today") LocalDate today);
}
