package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.Groups;
import com.secufusion.tenant.entity.Roles;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import lombok.Data;

import java.util.Set;
import java.util.stream.Collectors;

@Data
public class LoggedInUserDetailsBean {

    private String userId;
    private String keycloakUserId;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String sessionId;

    private String tenantId;
    private String tenantName;
    private String tenantStatus;

    private String userStatus;

    private boolean isAdminUser;

    private Set<String> groups;       // group names
    private Set<String> roles;        // role names
    private Set<String> scopes;       // allowed scopes
    private Set<String> unfilteredScopes; // scopes before filtering (optional)

    public static LoggedInUserDetailsBean from(User user, Tenant tenant,
                                               Set<String> finalScopes,
                                               Set<String> rawScopes) {

        LoggedInUserDetailsBean bean = new LoggedInUserDetailsBean();

        bean.setUserId(user.getPkUserId());
        bean.setKeycloakUserId(user.getKeycloakUserId());
        bean.setEmail(user.getEmail());
        bean.setUsername(user.getUserName());
        bean.setFirstName(user.getFirstName());
        bean.setLastName(user.getLastName());
        bean.setFullName(user.getFirstName() + " " + user.getLastName());

        bean.setTenantId(tenant.getTenantID());
        bean.setTenantName(tenant.getTenantName());
        bean.setTenantStatus(tenant.getStatus());
        bean.setUserStatus(user.getStatus());

        // Collect groups, roles, scopes
        bean.setGroups(
                user.getMappedGroups()
                        .stream()
                        .map(Groups::getName)
                        .collect(Collectors.toSet())
        );

        bean.setRoles(
                user.getMappedGroups()
                        .stream()
                        .flatMap(g -> g.getMappedRoles().stream())
                        .map(Roles::getName)
                        .collect(Collectors.toSet())
        );

        bean.setScopes(finalScopes);
        bean.setUnfilteredScopes(rawScopes);

        bean.setAdminUser(
                user.getMappedGroups()
                        .stream()
                        .anyMatch(g -> g.getIsAdmin() != null && g.getIsAdmin() == 'Y')
        );

        return bean;
    }
}
