package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shortcut")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shortcut {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_shortcut_id", length = 36)
    private String pkShortcutId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "display_order")
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_landingpage_id", nullable = false)
    @JsonBackReference
    private LandingPage landingPage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
