package com.secufusion.events.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Roles {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkRoleId;

    @Column(nullable = false)
    private String name;

    private String description;

    private Character isDefault = 'N';

    private Character isSuperRole = 'N';

    private Boolean active;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    @JsonIgnore
    private Tenant tenant;

    @ManyToMany
    @JoinTable(
            name = "role_scope_mapping",
            joinColumns = @JoinColumn(name = "fk_role_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_scope_id")
    )
    @JsonIgnore
    private Set<Scopes> scopes = new HashSet<>();

}
