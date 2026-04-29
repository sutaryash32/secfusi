package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "incident_assignees", indexes = {
        @Index(name = "idx_ia_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_ia_category", columnList = "fk_tenant_id, category"),
        @Index(name = "idx_ia_user", columnList = "fk_tenant_id, user_id"),
        @Index(name = "idx_ia_active", columnList = "fk_tenant_id, is_active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_assignee_tenant_category_user",
                columnNames = {"fk_tenant_id", "category", "user_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentAssignee extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_incident_assignee_id", nullable = false, updatable = false)
    private String pkIncidentAssigneeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private IncidentCategory category;  // NULL = all categories (fallback)

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "user_name", nullable = false, length = 200)
    private String userName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "assignment_order", nullable = false)
    @Builder.Default
    private Integer assignmentOrder = 0;
}
