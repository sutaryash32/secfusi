package com.secufusion.events.repository;

import com.secufusion.events.entity.ExtensionApiKeyRotationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ExtensionApiKeyRotationHistoryRepository
 *
 * Repository for tracking API key rotation history.
 */
@Repository
public interface ExtensionApiKeyRotationHistoryRepository extends JpaRepository<ExtensionApiKeyRotationHistory, String> {

    /**
     * Find rotation history for a specific key (as old key)
     */
    List<ExtensionApiKeyRotationHistory> findByOldApiKeyIdOrderByRotationDateDesc(String oldApiKeyId);

    /**
     * Find rotation history for a specific key (as new key)
     */
    List<ExtensionApiKeyRotationHistory> findByNewApiKeyIdOrderByRotationDateDesc(String newApiKeyId);

    /**
     * Find all rotations for a key chain (old or new)
     */
    @Query("SELECT r FROM ExtensionApiKeyRotationHistory r " +
           "WHERE r.oldApiKeyId = :keyId OR r.newApiKeyId = :keyId " +
           "ORDER BY r.rotationDate DESC")
    List<ExtensionApiKeyRotationHistory> findRotationChain(@Param("keyId") String keyId);

    /**
     * Find rotations by type
     */
    List<ExtensionApiKeyRotationHistory> findByRotationTypeOrderByRotationDateDesc(String rotationType);

    /**
     * Find recent rotations
     */
    Page<ExtensionApiKeyRotationHistory> findAllByOrderByRotationDateDesc(Pageable pageable);

    /**
     * Count rotations for a specific key
     */
    @Query("SELECT COUNT(r) FROM ExtensionApiKeyRotationHistory r " +
           "WHERE r.oldApiKeyId = :keyId OR r.newApiKeyId = :keyId")
    long countRotationsForKey(@Param("keyId") String keyId);
}
