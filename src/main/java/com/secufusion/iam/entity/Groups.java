package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "groups")
@Data
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    /** Group ↔ Users = M:N */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_group_map",
            joinColumns = @JoinColumn(name = "fk_group_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_user_id")
    )
    private Set<User> mappedUsers = new HashSet<>();

    /** Group ↔ Roles = M:N */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "group_role_map",
            joinColumns = @JoinColumn(name = "fk_group_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_role_id")
    )
    private Set<Roles> mappedRoles = new HashSet<>();
}
