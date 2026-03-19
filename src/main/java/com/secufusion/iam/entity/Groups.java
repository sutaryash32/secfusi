package com.secufusion.iam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "groups")
@Data
@ToString(exclude = {"mappedRoles"})
@EqualsAndHashCode(exclude = {"mappedRoles"})
@NoArgsConstructor
@AllArgsConstructor
public class Groups {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkGroupId;

    private Character isAdmin = 'N';
    private Character isDefault = 'N';

    private String description;

    private String name;

    private Boolean active;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    /** Each group belongs to one tenant */
    @Column(name = "fk_tenant_id", nullable = false)
    private String fkTenantId;

    /** Group ↔ Roles = M:N */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "group_role_map",
            joinColumns = @JoinColumn(name = "fk_group_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_role_id")
    )
    private Set<Roles> mappedRoles = new HashSet<>();
}
