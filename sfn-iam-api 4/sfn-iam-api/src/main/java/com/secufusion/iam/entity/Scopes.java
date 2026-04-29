package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "scopes")
public class Scopes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String pkScopeId;
    private String scopeName;
    private String displayName;
    private String description;
//    private String userType;
    //    private String userType;
    @Column(name = "menu_name")
    private String menuName;

    private String action;
    @Column(name = "sub_menu")
    private String subMenu;

    // 🔥 NEW: tenant type mapping (many-to-many)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tenant_type_scope_map",
            joinColumns = @JoinColumn(name = "fk_scope_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_tenant_type_id")
    )
    private Set<TenantType> tenantTypes;

    @Transient
    private Boolean enabled;

}
