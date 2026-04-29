package com.secufusion.iam.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Immutable
@Subselect("SELECT g.pk_group_id, g.name FROM groups g")
public class GroupsLean {

    private String pkGroupId;
    private String name;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "pk_group_id", referencedColumnName = "pk_group_id")
    private Set<RolesLean> roles;
}