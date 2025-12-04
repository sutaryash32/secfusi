package com.secufusion.iam.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

@Data
@Immutable
@AllArgsConstructor
@Subselect("SELECT r.pk_role_id, r.name FROM roles r")
public class RolesLean {

    private String pkRoleId;
    private String name;
}
