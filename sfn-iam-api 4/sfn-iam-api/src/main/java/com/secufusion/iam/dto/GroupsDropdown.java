package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupsDropdown {
    private String pkGroupId;
    private String name;
    private Set<RoleDropdownResponse> roles;
}
