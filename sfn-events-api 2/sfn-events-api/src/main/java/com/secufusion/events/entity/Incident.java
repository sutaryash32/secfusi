package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_incident_status", columnList = "status"),
        @Index(name = "idx_incident_priority", columnList = "priority"),
        @Index(name = "idx_incident_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_incident_category", columnList = "category"),
        @Index(name = "idx_incident_resolved_at", columnList = "resolved_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_incident_id", nullable = false, updatable = false)
    private String pkIncidentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "incident_number", nullable = false, length = 20)
    private String incidentNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private IncidentPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private IncidentCategory category;

    // Assignment
    @Column(name = "assigned_to", length = 36)
    private String assignedTo;

    @Column(name = "assigned_to_name", length = 200)
    private String assignedToName;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    // Resolution
    @Column(name = "resolved_by", length = 36)
    private String resolvedBy;

    @Column(name = "resolved_by_name", length = 200)
    private String resolvedByName;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_cause", length = 50)
    private RootCause rootCause;

    // Closure
    @Column(name = "closed_at")
    private Instant closedAt;

    // Metrics
    @Column(name = "event_count", nullable = false)
    @Builder.Default
    private Integer eventCount = 0;

    // Source tracking
    @Column(name = "source", nullable = false, length = 30)
    @Builder.Default
    private String source = "MANUAL";

    @Column(name = "auto_rule_name", length = 200)
    private String autoRuleName;

    // Merge support
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into")
    private Incident mergedInto;

    @Column(name = "is_merged", nullable = false)
    @Builder.Default
    private Boolean isMerged = false;

    // Escalation tracking
    @Column(name = "escalated_at")
    private Instant escalatedAt;
}
