package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "policy_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "azure_resource_id", nullable = false)
    private String azureResourceId;

    @Column(name = "azure_resource_name")
    private String azureResourceName;

    @Column(name = "assignment_type", nullable = false)
    private String assignmentType;

    /* ================= Tenant ================= */

    @Column(name = "fk_tenant_id", nullable = false)
    private String fkTenantId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /* ================= Policy Relationships ================= */

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_policy_id")
    private BrowserPolicy browserPolicy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_network_policy_id")
    private NetworkPolicy networkPolicy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_extension_policy_id")
    private ExtensionPolicy extensionPolicy;

    @PrePersist
    void onCreate() {
        this.assignedAt = LocalDateTime.now();
    }
}
