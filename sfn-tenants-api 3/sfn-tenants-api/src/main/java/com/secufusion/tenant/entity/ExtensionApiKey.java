package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "extension_api_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtensionApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pk_extension_api_key_id;

    private String tenantId;

    /**
     * SHA-256 hash of the raw API key
     */
    @Column(name = "key_hash", unique = true)
    private String keyHash;

    @Column(name = "key_prefix")
    private String keyPrefix;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "status")
    private String status;    // ACTIVE, INACTIVE, REVOKED etc.

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    private String createdBy;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
