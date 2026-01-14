package com.secufusion.iam.dto;

import lombok.Data;

import java.util.Set;

@Data
public class CreateScopeRequest {

    private String scopeName;
    private String displayName;
    private String description;
    private String userType;
    private String menuName;
    private String action;
    private String subMenu;
    private Set<String> tenantTypes;
}
