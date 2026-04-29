package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "api_flags")
public class ApiFlagEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key", unique = true, nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String path;

    private String description;

    @Column(name = "created_at")
    private Instant createdAt;

    // getters/setters
}