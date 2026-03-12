package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit history for events group actions.
 * Tracks group authorization, policy assignments, and member sync operations.
 */
@Entity
@Table(name = "events_group_history", indexes = {
        @Index(name = "idx_egh_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_egh_group", columnList = "fk_events_group_id"),
        @Index(name = "idx_egh_action", columnList = "action"),
        @Index(name = "idx_egh_timestamp", columnList = "performed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventsGroupHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pk_history_id", length = 36, updatable = false, nullable = false)
    private String pkHistoryId;

    @Column(name = "fk_tenant_id", nullable = false, length = 36)
    private String fkTenantId;

    @Column(name = "fk_events_group_id", nullable = false, length = 36)
    private String eventsGroupId;

    @Column(name = "group_name", length = 255)
    private String groupName;

    /**
     * Action type:
     * GROUP_AUTHORIZED, GROUP_DEAUTHORIZED,
     * POLICY_ASSIGNED, POLICY_REMOVED, POLICY_CHANGED,
     * MEMBERS_SYNCED, DEFAULT_POLICIES_ASSIGNED
     */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /**
     * Details about the action (e.g., policy names, member counts)
     */
    @Column(name = "details", length = 1000)
    private String details;

    /**
     * Policy assignment ID (if action is policy-related)
     */
    @Column(name = "policy_assignment_id", length = 36)
    private String policyAssignmentId;

    /**
     * Policy type: BROWSER, NETWORK, EXTENSION (if policy-related)
     */
    @Column(name = "policy_type", length = 20)
    private String policyType;

    /**
     * Policy name (for readable history)
     */
    @Column(name = "policy_name", length = 255)
    private String policyName;

    /**
     * Who performed the action (email or system identifier)
     */
    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    void onCreate() {
        this.performedAt = LocalDateTime.now();
    }
}
