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
    Map<String, Map<String, Set<String>>> permissionMatrix;
    Set<GroupsLean> mappedGroups;
    String fullName;
    String firstName;
    String lastName;
    String email;
    String username;
    String mobilePhone;
    TenantLean mappedTenant;
    private String ssoType; // "AZURE_AD", "OKTA", "GOOGLE", etc.

    /**
     * Whether this tenant is selfManaged (MSSP/Master MSSP with own users).
     * When true the UI shows the mode switcher (MSSP Mode ↔ My Org Mode).
     */
    private boolean selfManaged;

    /**
     * Enterprise-scoped permission matrix — only populated when selfManaged=true.
     * Used by the UI in "My Org Mode" to show Enterprise menu items.
     */
    private Map<String, Map<String, Set<String>>> myOrgPermissionMatrix;
}
