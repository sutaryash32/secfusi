package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "incident_events", indexes = {
        @Index(name = "idx_ie_incident", columnList = "fk_incident_id"),
        @Index(name = "idx_ie_event", columnList = "fk_event_id"),
        @Index(name = "idx_ie_tenant", columnList = "fk_tenant_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_incident_event", columnNames = {"fk_incident_id", "fk_event_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentEvent extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_incident_event_id", nullable = false, updatable = false)
    private String pkIncidentEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_event_id", nullable = false)
    private Event event;

    @Column(name = "fk_tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "linked_by", length = 100)
    private String linkedBy;
}
