package com.secufusion.iam.dto;

import com.secufusion.iam.entity.*;
import lombok.Data;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class LoggedInUserDetailsBean {

    private String userId;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;

    private String tenantId;
    private String tenantName;
    private String tenantStatus;

    private String userStatus;

    private boolean isAdminUser;

    private Set<String> groups;       // group names
    private Set<String> roles;        // role names
    private Set<String> scopes;       // allowed scopes
    private Set<String> unfilteredScopes;
    private Map<String, Map<String, Set<String>>> permissionMatrix;

    public static LoggedInUserDetailsBean from(
            User user,
            Tenant tenant,
            Set<String> finalScopes,
            Set<String> rawScopes
    ) {

        LoggedInUserDetailsBean bean = new LoggedInUserDetailsBean();

        bean.setUserId(user.getPkUserId());
        bean.setEmail(user.getEmail());
        bean.setUsername(user.getUserName());
        bean.setFirstName(user.getFirstName());
        bean.setLastName(user.getLastName());
        bean.setFullName(user.getFirstName() + " " + user.getLastName());

        bean.setTenantId(tenant.getTenantID());
        bean.setTenantName(tenant.getTenantName());
        bean.setTenantStatus(tenant.getStatus());
        bean.setUserStatus(user.getStatus());

        // -------------------------------
        // Groups
        // -------------------------------
        bean.setGroups(
                user.getMappedGroups()
                        .stream()
                        .map(Groups::getName)
                        .collect(Collectors.toSet())
        );

        // -------------------------------
        // Roles
        // -------------------------------
        bean.setRoles(
                user.getMappedGroups()
                        .stream()
                        .flatMap(g -> g.getMappedRoles().stream())
                        .map(Roles::getName)
                        .collect(Collectors.toSet())
        );

        // -------------------------------
        // Flat scopes
        // -------------------------------
        bean.setScopes(finalScopes);
        bean.setUnfilteredScopes(rawScopes);

        // -------------------------------
        // Admin flag
        // -------------------------------
        bean.setAdminUser(
                user.getMappedGroups()
                        .stream()
                        .anyMatch(g -> g.getIsAdmin() != null && g.getIsAdmin() == 'Y')
        );

        // -------------------------------
        // ✅ Permission Matrix (OLD LOGIC)
        // menu_name → sub_menu → actions
        // -------------------------------
        Map<String, Map<String, Set<String>>> permissionMatrix =
                user.getMappedGroups()
                        .stream()
                        .flatMap(g -> g.getMappedRoles().stream())
                        .flatMap(r -> r.getScopes().stream())
                        // user actually has the scope
                        .filter(scope -> finalScopes.contains(scope.getScopeName()))
                        .collect(Collectors.groupingBy(
                                Scopes::getMenuName,
                                Collectors.groupingBy(
                                        Scopes::getSubMenu,
                                        Collectors.mapping(
                                                Scopes::getAction,
                                                Collectors.toSet()
                                        )
                                )
                        ));

        bean.setPermissionMatrix(permissionMatrix);

        return bean;
    }
}
