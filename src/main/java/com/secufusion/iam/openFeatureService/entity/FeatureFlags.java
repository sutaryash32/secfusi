package com.secufusion.iam.openFeatureService.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "feature_flags")
public class FeatureFlags {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_key", unique = true, nullable = false)
    private String flagKey;

    @Column(nullable = false)
    private boolean enabled;

    private String description;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // getters/setters
}

