package com.secufusion.iam.service;

import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.GroupsRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for group lifecycle operations:
 * - create default or custom groups
 * - assign roles and users to groups
 * - fetch and update groups with tenant scoping
 * <p>
 * Logging is added to trace important decision points and data changes.
 */
@Service
@Slf4j
public class GroupService {

    @Autowired
    private GroupsRepository groupsRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtl jwtUtl;

    /**
     * Create or get a default group for a tenant.
     *
     * @param tenantId    tenant identifier
     * @param groupName   name of the default group
     * @param isAdmin     whether this default group is admin
     * @param defaultUser user creating the group (used for audit)
     * @return existing or newly created Groups entity
     */
    @Transactional
    public Groups createOrGetDefaultGroup(String tenantId, String groupName, boolean isAdmin, String defaultUser) {
        log.info("createOrGetDefaultGroup: start - tenantId={}, groupName={}, isAdmin={}, defaultUser={}",
                tenantId, groupName, isAdmin, defaultUser);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> {
                    log.error("createOrGetDefaultGroup: tenant not found tenantId={}", tenantId);
                    return new ResourceNotFoundException("Tenant not found");
                });

        // Try to find existing group for tenant
        Groups result = groupsRepository.findByNameAndTenantId(groupName, tenantId)
                .orElseGet(() -> {
                    log.info("createOrGetDefaultGroup: Group '{}' not found for tenantId={}. Creating new default group.",
                            groupName, tenantId);

                    Groups group = new Groups();
                    group.setIsAdmin(isAdmin ? 'Y' : 'N');
                    group.setIsDefault('Y');
                    group.setDescription(groupName + " default group");
                    group.setName(groupName);
                    group.setTenantId(tenantId);
                    group.setCreatedBy(defaultUser);
                    group.setCreatedTime(LocalDateTime.now());
                    group.setActive(true);
                    // Initialize collections
                    group.setMappedRoles(new HashSet<>());

                    Groups saved = groupsRepository.save(group);
                    log.debug("createOrGetDefaultGroup: created group id={} name={} tenantId={}",
                            saved.getPkGroupId(), saved.getName(), tenantId);
                    return saved;
                });

        log.info("createOrGetDefaultGroup: end - returning group id={} name={}", result.getPkGroupId(), result.getName());
        return result;
    }

    /**
     * Assign role to group IF NOT ALREADY MAPPED
     */
    @Transactional
    public void assignRoleToGroup(Groups group, Roles role) {
        if (group == null || role == null) {
            log.warn("assignRoleToGroup: received null group or role. group={}, role={}", group, role);
            return;
        }

        log.debug("assignRoleToGroup: start - groupId={} roleId={} roleName={}",
                group.getPkGroupId(), role.getPkRoleId(), role.getName());

        if (group.getMappedRoles() == null)
            group.setMappedRoles(new HashSet<>());

        boolean exists = group.getMappedRoles().stream()
                .anyMatch(r -> r.getPkRoleId().equals(role.getPkRoleId()));

        if (exists) {
            log.info("assignRoleToGroup: Role '{}' already mapped to group '{}'", role.getName(), group.getName());
            return;
        }

        group.getMappedRoles().add(role);
        groupsRepository.save(group);

        log.info("assignRoleToGroup: Assigned role '{}' (id={}) to group '{}' (id={})",
                role.getName(), role.getPkRoleId(), group.getName(), group.getPkGroupId());
    }

    /**
     * Assign user to group IF NOT ALREADY MAPPED
     */
    @Transactional
    public void assignUserToGroup(Groups group, User user) {
        if (group == null || user == null) {
            log.warn("assignUserToGroup: received null group or user. group={}, user={}", group, user);
            return;
        }

        log.debug("assignUserToGroup: start - groupId={} userId={} userName={}",
                group.getPkGroupId(), user.getPkUserId(), user.getUserName());

        Groups save = groupsRepository.save(group);
        User save1 = userRepository.save(user);

        log.info("assignUserToGroup: Assigned user '{}' (id={}) to group '{}' (id={})",
                user.getUserName(), user.getPkUserId(), group.getName(), group.getPkGroupId());
    }

    /**
     * Create a custom group for the tenant identified in the request.
     */
    public Groups createGroup(HttpServletRequest request, Groups groups) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User creator = jwtUtl.getUserFromRequest(request);

        String tenantId = tenant.getTenantID();

        // Prevent duplicate group name inside tenant
        groupsRepository.findByNameAndTenantId(groups.getName().trim(), tenantId)
                .ifPresent(e -> {
                    throw new ResourceNotFoundException("Group with this name already exists");
                });

        groups.setTenantId(tenantId);
        groups.setCreatedBy(creator.getUserName());
        groups.setActive(true);
        groups.setCreatedTime(LocalDateTime.now());
        groups.setIsAdmin('N');
        groups.setIsDefault('N');

        // ---------------------------
        // MAP ROLES
        // ---------------------------
        if (groups.getMappedRoles() == null) groups.setMappedRoles(new HashSet<>());

        return groupsRepository.save(groups);
    }


    /**
     * Retrieve a group by id ensuring tenant access.
     */
    public Groups getGroupById(HttpServletRequest request, String id) {
        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        if (tenantFromRequest == null) {
            log.error("getGroupById: tenant not present in request");
            throw new ResourceNotFoundException("Tenant not found in request");
        }
        log.info("getGroupById: Fetching groupId={} for tenantId={}", id, tenantFromRequest.getTenantID());
        Groups group = groupsRepository.findGroupAccessibleByTenant(id, tenantFromRequest.getTenantID());
        if (group == null) {
            log.warn("getGroupById: group not found or not accessible - groupId={} tenantId={}", id, tenantFromRequest.getTenantID());
        } else {
            log.debug("getGroupById: found group id={} name={}", group.getPkGroupId(), group.getName());
        }
        return group;
    }

    /**
     * Get all non-admin and non-default groups for the tenant.
     */
    public List<Groups> getAllGroups(HttpServletRequest request) {
        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        if (tenantFromRequest == null) {
            log.error("getAllGroups: tenant not present in request");
            throw new ResourceNotFoundException("Tenant not found in request");
        }
        String tenantId = tenantFromRequest.getTenantID();
        log.info("getAllGroups: Fetching all groups for tenantId={}", tenantId);
        List<Groups> result = groupsRepository.findByTenantId(tenantId)
                .stream()
                .peek(g -> {
                    String creatorId = g.getCreatedBy();
                    if (creatorId != null && !creatorId.isBlank()) {
                        userRepository.findById(creatorId)
                                .ifPresent(u -> {
                                    if (u.getEmail() != null) {
                                        g.setCreatedBy(u.getEmail());
                                    }
                                });
                    }
                })
                .toList();

        log.debug("getAllGroups: found {} groups for tenantId={}", result.size(), tenantId);
        return result;
    }

    /**
     * Update a group (name, description, isAdmin, active, mapped roles).
     * Ensures tenant access and handles role reconciliation.
     */
    @Transactional
    public Groups updateGroup(HttpServletRequest request, String id, Groups incoming) throws AccessDeniedException {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User updater = jwtUtl.getUserFromRequest(request);

        Groups existing = groupsRepository.findGroupAccessibleByTenant(id, tenant.getTenantID());
        if (existing == null) {
            throw new AccessDeniedException("Group not accessible");
        }

        if(existing.getIsAdmin() != null && existing.getIsAdmin() == 'Y'
                && existing.getIsDefault() != null && existing.getIsDefault() == 'Y') {
            throw new AccessDeniedException("Admin group cannot be modified");
        }

        // ---------------------------
        // UNIQUE NAME CHECK
        // ---------------------------
        if (incoming.getName() != null && !incoming.getName().equals(existing.getName())) {
            groupsRepository.findByNameAndTenantId(incoming.getName(), existing.getTenantId())
                    .ifPresent(conflict -> {
                        if (!conflict.getPkGroupId().equals(existing.getPkGroupId())) {
                            throw new ResourceNotFoundException("Group name already exists");
                        }
                    });
            existing.setName(incoming.getName());
        }

        if (incoming.getDescription() != null) existing.setDescription(incoming.getDescription());
        if (incoming.getIsAdmin() != null) existing.setIsAdmin(incoming.getIsAdmin());
        if (incoming.getActive() != null) existing.setActive(incoming.getActive());

        // ---------------------------
        // UPDATE MAPPED ROLES
        // ---------------------------
        if (incoming.getMappedRoles() != null) {
            existing.setMappedRoles(incoming.getMappedRoles());
        }

        existing.setUpdatedBy(updater.getUserName());
        existing.setUpdatedTime(LocalDateTime.now());

        return groupsRepository.save(existing);
    }

    public List<Groups> getGroupsByTenant(HttpServletRequest request, String tenantId) {
        log.info("getGroupsByTenant: Fetching groups for tenantId={}", tenantId);
        //validate tenant that requesting tenant can access requested tenantId by parent-child relationship
        Tenant requestingTenant = jwtUtl.getTenantFromRequest(request);
        if (requestingTenant == null) {
            log.error("getGroupsByTenant: tenant not present in request");
            throw new ResourceNotFoundException("Tenant not found in request");
        }
        //validate by parent heirarchy
        if (!tenantRepository.isTenantAccessibleByAnother(requestingTenant.getTenantID(), tenantId)) {
            log.error("getGroupsByTenant: tenantId={} not accessible by requesting tenantId={}",
                    tenantId, requestingTenant.getTenantID());
            throw new ResourceNotFoundException("Requested tenant not accessible");
        }
        List<Groups> groups = groupsRepository.findByTenantId(tenantId);
        log.debug("getGroupsByTenant: found {} groups for tenantId={}", groups.size(), tenantId);
        return groups;
    }

    public boolean deleteGroupsByTenantId(String tenantID) {
        log.info("deleteGroupsByTenantId: Deleting groups for tenantId={}", tenantID);
        List<Groups> groupsToDelete = groupsRepository.findByTenantId(tenantID);
        groupsRepository.deleteAll(groupsToDelete);
        log.debug("deleteGroupsByTenantId: Deleted {} groups for tenantId={}", groupsToDelete.size(), tenantID);
        return true;
    }
}