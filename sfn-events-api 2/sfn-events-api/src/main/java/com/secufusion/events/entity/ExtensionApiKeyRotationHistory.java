package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * ExtensionApiKeyRotationHistory Entity
 *
 * Tracks all API key rotations for audit purposes.
 * Records old and new key information along with rotation metadata.
 */
@Entity
@Table(name = "extension_api_key_rotation_history", indexes = {
    @Index(name = "idx_rotation_old_key", columnList = "old_api_key_id"),
    @Index(name = "idx_rotation_new_key", columnList = "new_api_key_id"),
    @Index(name = "idx_rotation_date", columnList = "rotation_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionApiKeyRotationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_rotation_id", length = 36, nullable = false, updatable = false)
    private String pkRotationId;

    /**
     * Reference to the old (revoked) API key
     */
    @Column(name = "fk_old_api_key_id", nullable = false, length = 36)
    private String oldApiKeyId;

    /**
     * Reference to the new (active) API key
     */
    @Column(name = "fk_new_api_key_id", nullable = false, length = 36)
    private String newApiKeyId;

    /**
     * Prefix of the old key for display
     */
    @Column(name = "old_key_prefix", length = 12)
    private String oldKeyPrefix;

    /**
     * Prefix of the new key for display
     */
    @Column(name = "new_key_prefix", length = 12)
    private String newKeyPrefix;

    /**
     * Type of rotation: MANUAL, AUTOMATIC, COMPROMISED
     */
    @Column(name = "rotation_type", nullable = false, length = 20)
    private String rotationType;

    /**
     * User who performed the rotation
     */
    @Column(name = "rotated_by", length = 100)
    private String rotatedBy;

    /**
     * Date and time of rotation
     */
    @CreationTimestamp
    @Column(name = "rotation_date", nullable = false, updatable = false)
    private Instant rotationDate;

    /**
     * IP address from which rotation was initiated
     */
    @Column(name = "rotation_ip", length = 45)
    private String rotationIp;

    /**
     * Reason for rotation
     */
    @Column(name = "reason", length = 1000)
    private String reason;

    /**
     * Foreign key relationship to old API key
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_old_api_key_id", referencedColumnName = "pk_extension_api_key_id", insertable = false, updatable = false)
    private ExtensionApiKey oldApiKey;

    /**
     * Foreign key relationship to new API key
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_new_api_key_id", referencedColumnName = "pk_extension_api_key_id", insertable = false, updatable = false)
    private ExtensionApiKey newApiKey;
}
