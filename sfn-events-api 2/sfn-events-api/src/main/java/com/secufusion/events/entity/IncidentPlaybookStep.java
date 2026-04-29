package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "incident_playbook_steps", indexes = {
        @Index(name = "idx_ips_playbook", columnList = "fk_incident_playbook_id"),
        @Index(name = "idx_ips_tenant", columnList = "fk_tenant_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_ips_playbook_step",
                columnNames = {"fk_incident_playbook_id", "step_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentPlaybookStep extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_incident_playbook_step_id", nullable = false, updatable = false)
    private String pkIncidentPlaybookStepId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_incident_playbook_id", nullable = false)
    private IncidentPlaybook incidentPlaybook;

    @Column(name = "fk_tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = false;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "completed_by", length = 36)
    private String completedBy;

    @Column(name = "completed_by_name", length = 200)
    private String completedByName;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
