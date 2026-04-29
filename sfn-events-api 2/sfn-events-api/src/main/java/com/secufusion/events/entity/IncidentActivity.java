package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "incident_activities", indexes = {
        @Index(name = "idx_ia_incident", columnList = "fk_incident_id"),
        @Index(name = "idx_ia_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_ia_action", columnList = "action")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentActivity extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_incident_activity_id", nullable = false, updatable = false)
    private String pkIncidentActivityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_incident_id", nullable = false)
    private Incident incident;

    @Column(name = "fk_tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private IncidentActivityAction action;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "performed_by", length = 36)
    private String performedBy;

    @Column(name = "performed_by_name", length = 200)
    private String performedByName;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;
}
