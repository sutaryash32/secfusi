package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleDropdownResponse {
    private String pkRoleId;
    private String name;
}
