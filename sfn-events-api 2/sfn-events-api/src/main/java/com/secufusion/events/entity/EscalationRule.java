package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "escalation_rules", indexes = {
        @Index(name = "idx_er_tenant", columnList = "fk_tenant_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_er_tenant_name", columnNames = {"fk_tenant_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalationRule extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_escalation_rule_id", nullable = false, updatable = false)
    private String pkEscalationRuleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_priority", length = 20)
    private IncidentPriority triggerPriority;

    @Column(name = "unassigned_minutes")
    private Integer unassignedMinutes;

    @Column(name = "unresolved_hours")
    private Integer unresolvedHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalate_to_priority", length = 20)
    private IncidentPriority escalateToPriority;

    @Column(name = "notify_role", length = 100)
    private String notifyRole;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
