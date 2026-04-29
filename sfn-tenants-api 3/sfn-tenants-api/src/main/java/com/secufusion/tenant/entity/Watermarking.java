package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "watermarking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Watermarking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_watermarking_id", length = 36)
    private String pkWatermarkingId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "image_url", length = 200)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
