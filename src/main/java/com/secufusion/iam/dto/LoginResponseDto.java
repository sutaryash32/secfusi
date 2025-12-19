package com.secufusion.iam.dto;

import com.secufusion.iam.entity.GroupsLean;
import com.secufusion.iam.entity.TenantLean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    private String userId;
    private String userType;
    private String accessToken;
    private String tenantId;
//    Set<String> mappedScopes;
    private Map<String, Map<String, Set<String>>> permissionMatrix;
    Set<GroupsLean> mappedGroups;
    String fullName;
    String firstName;
    String lastName;
    String email;
    String username;
    String mobilePhone;
    TenantLean mappedTenant;
}
