package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateScopeRequest {

    private String scopeName;
    private String displayName;
    private String description;
    private String menuName;
    private String action;
    private String subMenu;
}
