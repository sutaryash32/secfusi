package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "playbook_templates", indexes = {
        @Index(name = "idx_pt_tenant", columnList = "fk_tenant_id"),
        @Index(name = "idx_pt_category", columnList = "fk_tenant_id, category")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_pt_tenant_name", columnNames = {"fk_tenant_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaybookTemplate extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_playbook_template_id", nullable = false, updatable = false)
    private String pkPlaybookTemplateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private IncidentCategory category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> steps;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
