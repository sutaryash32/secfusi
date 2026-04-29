package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "smtp_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmtpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_smtp_config_id", length = 36)
    private String pkSmtpConfigId;

    /**
     * Tenant ID - NULL means default/global SMTP configuration.
     */
    @Column(name = "fk_tenant_id", unique = true, length = 36)
    private String fkTenantId;

    @Column(length = 255, nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(length = 10)
    private String auth = "true";

    @Column(length = 10)
    private String starttls = "true";

    @Column(length = 10)
    private String ssl = "false";

    @Column(length = 255, nullable = false)
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(length = 255, nullable = false)
    private String password;

    @Column(name = "from_email", length = 255, nullable = false)
    private String fromEmail;

    @Column(name = "from_name", length = 100)
    private String fromName;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
