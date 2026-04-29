package com.secufusion.tenant.dto;


import com.secufusion.tenant.entity.Scopes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolesDto {
    private String pkRoleId;
    private String name;
    private String description;
    private Character isDefault;
    private Character isSuperRole;
    private Set<Scopes> scopes;
    private Boolean active;
}