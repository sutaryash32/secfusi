package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "urlfilter")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UrlFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_urlfilter_id", length = 36)
    private String pkUrlFilterId;

    @Column(nullable = false, length = 10)
    private String filterType;     // WHITELIST / BLACKLIST

    @Column(nullable = false, length = 20)
    private String patternType;    // DOMAIN / REGEX / URL

    @Column(nullable = false, length = 255)
    private String pattern;

    @Column(length = 255)
    private String description;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_network_policy_id")
    @JsonBackReference("networkPolicy-urlFilters")
    private NetworkPolicy networkPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_extension_policy_id")
    @JsonBackReference("extensionPolicy-urlFilters")
    private ExtensionPolicy extensionPolicy;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
