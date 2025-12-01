package com.secufusion.iam.service;

import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.entity.Groups;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.GroupsRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class GroupService {

    @Autowired
    private GroupsRepository groupsRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    /** Create or get a group */
    @Transactional
    public Groups createOrGetDefaultGroup(String tenantId, String groupName, boolean isAdmin, String defaultUser) {
        log.info("Checking group '{}' for tenantId={}", groupName, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        return groupsRepository.findByNameAndTenant_TenantID(groupName, tenantId)
                .orElseGet(() -> {
                    log.info("Group '{}' not found. Creating new...", groupName);

                    Groups group = new Groups();
                    group.setIsAdmin(isAdmin ? 'Y' : 'N');
                    group.setIsDefault('Y');
                    group.setDescription(groupName + " default group");
                    group.setName(groupName);
                    group.setTenant(tenant);
                    group.setCreatedBy(defaultUser);
                    group.setCreatedTime(LocalDateTime.now());
                    group.setActive(true);
                    // Initialize collections
                    group.setMappedUsers(new HashSet<>());
                    group.setMappedRoles(new HashSet<>());

                    return groupsRepository.save(group);
                });
    }

    /** Assign role to group IF NOT ALREADY MAPPED */
    @Transactional
    public void assignRoleToGroup(Groups group, Roles role) {

        if (group.getMappedRoles() == null)
            group.setMappedRoles(new HashSet<>());

        boolean exists = group.getMappedRoles().stream()
                .anyMatch(r -> r.getPkRoleId().equals(role.getPkRoleId()));

        if (exists) {
            log.info("Role '{}' already mapped to group '{}'", role.getName(), group.getName());
            return;
        }

        group.getMappedRoles().add(role);
        groupsRepository.save(group);

        log.info("Assigned role '{}' to group '{}'", role.getName(), group.getName());
    }

    /** Assign user to group IF NOT ALREADY MAPPED */
    @Transactional
    public void assignUserToGroup(Groups group, User user) {

        if (group.getMappedUsers() == null)
            group.setMappedUsers(new HashSet<>());

        boolean exists = group.getMappedUsers().stream()
                .anyMatch(u -> u.getPkUserId().equals(user.getPkUserId()));

        if (exists) {
            log.info("User '{}' already mapped to group '{}'", user.getUserName(), group.getName());
            return;
        }

        group.getMappedUsers().add(user);
        groupsRepository.save(group);

        log.info("Assigned user '{}' to group '{}'", user.getUserName(), group.getName());
    }

    public Groups createGroup(Groups groups) {
        groupsRepository.existsByName(groups.getName())
                .ifPresent(existingGroup -> {
                    throw new ResourceNotFoundException("Group with name '" + groups.getName() + "' already exists.");
                });
        groups.setActive(true);
        groups.setCreatedTime(LocalDateTime.now());
        groups.setIsDefault('N');
        groups.setIsAdmin('N');
        return groupsRepository.save(groups);
    }

    public Groups getGroupById(String id) {
        return groupsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + id));
    }

    public List<Groups> getAllGroups() {
        return groupsRepository.findAll();
    }

    public List<GroupsDropdown> getGroupsForDropdown() {
        List<Groups> groupsList = groupsRepository.findAll();
        return groupsList.stream()
                .filter(g -> !((g.getIsAdmin() != null && g.getIsAdmin() == 'Y')
                             || (g.getIsDefault() != null && g.getIsDefault() == 'Y')))
                .map(group -> {
                    Set<RoleDropdownResponse> roles = Optional.ofNullable(group.getMappedRoles())
                            .orElse(Collections.emptySet())
                            .stream()
                            .map(r -> new RoleDropdownResponse(r.getPkRoleId(), r.getName()))
                            .collect(java.util.stream.Collectors.toSet());

                    return new GroupsDropdown(group.getPkGroupId(), group.getName(), roles);
                })
                .toList();
    }
}
