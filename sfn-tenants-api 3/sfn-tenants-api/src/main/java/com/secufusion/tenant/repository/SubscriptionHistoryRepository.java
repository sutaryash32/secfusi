package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    /**
     * Find all history records for a tenant, ordered by most recent first.
     */
    @Query("SELECT h FROM SubscriptionHistory h WHERE h.tenant.tenantID = :tenantId ORDER BY h.changedAt DESC")
    List<SubscriptionHistory> findByTenantIdOrderByChangedAtDesc(@Param("tenantId") String tenantId);

    /**
     * Find all history records for a subscription, ordered by most recent first.
     */
    @Query("SELECT h FROM SubscriptionHistory h WHERE h.subscription.pkSubscriptionId = :subscriptionId ORDER BY h.changedAt DESC")
    List<SubscriptionHistory> findBySubscriptionIdOrderByChangedAtDesc(@Param("subscriptionId") Long subscriptionId);

    /**
     * Find history records by action type for a tenant.
     */
    @Query("SELECT h FROM SubscriptionHistory h WHERE h.tenant.tenantID = :tenantId AND h.action = :action ORDER BY h.changedAt DESC")
    List<SubscriptionHistory> findByTenantIdAndAction(@Param("tenantId") String tenantId,
                                                       @Param("action") SubscriptionHistory.Action action);

    /**
     * Find history records within a date range for a tenant.
     */
    @Query("SELECT h FROM SubscriptionHistory h WHERE h.tenant.tenantID = :tenantId AND h.changedAt BETWEEN :startDate AND :endDate ORDER BY h.changedAt DESC")
    List<SubscriptionHistory> findByTenantIdAndDateRange(@Param("tenantId") String tenantId,
                                                          @Param("startDate") Instant startDate,
                                                          @Param("endDate") Instant endDate);

    /**
     * Count upgrades for a tenant.
     */
    @Query("SELECT COUNT(h) FROM SubscriptionHistory h WHERE h.tenant.tenantID = :tenantId AND h.action = 'UPGRADED'")
    long countUpgradesByTenantId(@Param("tenantId") String tenantId);

    /**
     * Get latest history record for a tenant.
     */
    @Query("SELECT h FROM SubscriptionHistory h WHERE h.tenant.tenantID = :tenantId ORDER BY h.changedAt DESC LIMIT 1")
    SubscriptionHistory findLatestByTenantId(@Param("tenantId") String tenantId);

    @Modifying
    @Query("DELETE FROM SubscriptionHistory h WHERE h.tenant.tenantID = :tenantId")
    void deleteByTenantId(@Param("tenantId") String tenantId);
}
