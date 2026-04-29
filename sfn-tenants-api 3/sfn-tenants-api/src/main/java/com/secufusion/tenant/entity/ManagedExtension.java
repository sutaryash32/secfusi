package com.secufusion.tenant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "managed_extensions")
@Data
public class ManagedExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_managed_extension_id", length = 36)
    private String pkManagedExtensionId;

    @Column(name = "action")
    @Enumerated(EnumType.STRING)
    @com.fasterxml.jackson.annotation.JsonProperty("extensionPolicyType")
    @com.fasterxml.jackson.annotation.JsonAlias("extensionPolicyType")
    private ExtensionAction action = ExtensionAction.ALLOW_ALL; // ALLOW_ALL / BLOCK_ALL / ALLOW_LIST / BLOCK_LIST

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_managed_extension_id")
    private List<ExtensionDetail> extensions;

    @Column(name = "enforcement_action")
    private String enforcementAction = "WARN_USER"; // WARN_USER / BLOCK_BROWSER

    @Column(name = "warning_message", length = 1000)
    private String warningMessage = "A prohibited extension has been detected. Please uninstall it to comply with company policy.";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
