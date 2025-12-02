package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolesDto {
    private String pkRoleId;
    private String name;
    private String description;
    private Character isDefault;
    private Character isSuperRole;
    private Boolean active;
}
