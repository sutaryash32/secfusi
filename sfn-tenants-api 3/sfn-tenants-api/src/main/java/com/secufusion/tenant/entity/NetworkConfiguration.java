package com.secufusion.tenant.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "networkconfiguration")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_networkconfiguration_id", length = 36)
    private String pkNetworkConfigurationId;

    private String proxyMode;
    private String pacUrl;
    private String proxyServers;

    @Column(columnDefinition = "TEXT")
    private String bypassList;

    private boolean useSecufusionIdpProxy;

    @Column(columnDefinition = "TEXT")
    private String customIdpHostnames;

    @Column(name = "identity_provider", length = 50)
    private String identityProvider;

    @Column(name = "hostnames", columnDefinition = "jsonb")
    private JsonNode hostnames;

    private LocalDateTime updatedAt;

    /* ===================== Relationships ===================== */

//    @OneToOne(cascade = CascadeType.ALL)
//    @JoinColumn(name = "fk_identityprovider_id")

    /* ===================== Lifecycle ===================== */

    @PrePersist
    void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
