package com.secufusion.iam.dto;

import com.secufusion.iam.entity.Scopes;
import jakarta.validation.metadata.Scope;
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
