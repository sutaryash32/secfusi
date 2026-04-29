package com.secufusion.events.repository;

import com.secufusion.events.entity.ExtensionApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ExtensionApiKeyRepository
 *
 * Repository for managing Extension API Keys.
 * Provides methods for key lifecycle management, validation, and statistics.
 */
@Repository
public interface ExtensionApiKeyRepository extends JpaRepository<ExtensionApiKey, String> {

    /**
     * Find API key by hash (for validation)
     */
    Optional<ExtensionApiKey> findByKeyHash(String keyHash);

    /**
     * Find API key by prefix (for display/search)
     */
    Optional<ExtensionApiKey> findByKeyPrefix(String keyPrefix);

    /**
     * Find all keys for a tenant
     */
    Page<ExtensionApiKey> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Find keys by tenant and status
     */
    Page<ExtensionApiKey> findByTenantIdAndStatusOrderByCreatedAtDesc(
        String tenantId, String status, Pageable pageable);

    /**
     * Find active keys for a tenant
     */
    List<ExtensionApiKey> findByTenantIdAndStatus(String tenantId, String status);

    /**
     * Check if active key exists for tenant
     */
    boolean existsByTenantIdAndStatus(String tenantId, String status);

    /**
     * Count keys by tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Count keys by tenant and status
     */
    long countByTenantIdAndStatus(String tenantId, String status);

    /**
     * Find expired active keys that need to be disabled
     */
    @Query("SELECT k FROM ExtensionApiKey k WHERE k.status = 'ACTIVE' " +
           "AND k.expiresAt IS NOT NULL AND k.expiresAt < :now")
    List<ExtensionApiKey> findExpiredActiveKeys(@Param("now") LocalDateTime now);

    /**
     * Find keys expiring soon (within threshold days)
     */
    @Query("SELECT k FROM ExtensionApiKey k WHERE k.status = 'ACTIVE' " +
           "AND k.expiresAt IS NOT NULL " +
           "AND k.expiresAt BETWEEN :now AND :threshold")
    List<ExtensionApiKey> findKeysExpiringSoon(
        @Param("now") LocalDateTime now,
        @Param("threshold") LocalDateTime threshold);

    /**
     * Find keys by tenant with expiry information
     */
    @Query("SELECT k FROM ExtensionApiKey k WHERE k.tenantId = :tenantId " +
           "ORDER BY k.expiresAt ASC NULLS LAST")
    List<ExtensionApiKey> findByTenantIdOrderByExpiryAsc(@Param("tenantId") String tenantId);

    /**
     * Get statistics for all tenants or specific tenant
     */
    @Query("SELECT k.status as status, COUNT(k) as count FROM ExtensionApiKey k " +
           "WHERE (:tenantId IS NULL OR k.tenantId = :tenantId) " +
           "GROUP BY k.status")
    List<Object[]> getKeyStatisticsByStatus(@Param("tenantId") String tenantId);

    /**
     * Count keys expiring soon for a tenant
     */
    @Query("SELECT COUNT(k) FROM ExtensionApiKey k WHERE k.tenantId = :tenantId " +
           "AND k.status = 'ACTIVE' " +
           "AND k.expiresAt IS NOT NULL " +
           "AND k.expiresAt BETWEEN :now AND :threshold")
    long countKeysExpiringSoon(
        @Param("tenantId") String tenantId,
        @Param("now") LocalDateTime now,
        @Param("threshold") LocalDateTime threshold);

    /**
     * Search keys by name or prefix
     */
    @Query("SELECT k FROM ExtensionApiKey k WHERE k.tenantId = :tenantId " +
           "AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR k.keyPrefix LIKE CONCAT(:searchTerm, '%'))")
    Page<ExtensionApiKey> searchKeys(
        @Param("tenantId") String tenantId,
        @Param("searchTerm") String searchTerm,
        Pageable pageable);
}
