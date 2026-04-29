package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "incident_playbooks", indexes = {
        @Index(name = "idx_ip_incident", columnList = "fk_incident_id"),
        @Index(name = "idx_ip_tenant", columnList = "fk_tenant_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_ip_incident_template",
                columnNames = {"fk_incident_id", "fk_playbook_template_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentPlaybook extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_incident_playbook_id", nullable = false, updatable = false)
    private String pkIncidentPlaybookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_playbook_template_id", nullable = false)
    private PlaybookTemplate playbookTemplate;

    @Column(name = "fk_tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps;

    @Column(name = "completed_steps", nullable = false)
    @Builder.Default
    private Integer completedSteps = 0;

    @Column(name = "is_complete", nullable = false)
    @Builder.Default
    private Boolean isComplete = false;
}
