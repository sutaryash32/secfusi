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
     * Create a custom group for the tenant identified in the request.
     */
    public Groups createGroup(HttpServletRequest request, Groups groups) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        User creator = jwtUtl.getUserFromRequest(request);

        String tenantId = tenant.getTenantID();

        // Prevent duplicate group name inside tenant
        groupsRepository.findByNameAndFkTenantId(groups.getName().trim(), tenantId)
                .ifPresent(e -> {
                    throw new ResourceNotFoundException("Group with this name already exists");
                });

        groups.setFkTenantId(tenantId);
        groups.setCreatedBy(creator.getUserName());
        groups.setActive(true);
        groups.setCreatedTime(LocalDateTime.now());
        groups.setIsAdmin('N');
        groups.setIsDefault('N');

        // ---------------------------
        // MAP ROLES - ensure roles belong to same tenant (if role.tenantId == null then allow)
        // ---------------------------
       if (groups.getMappedRoles() == null) {
           groups.setMappedRoles(new HashSet<>());
       } else {
           String groupTenantId = groups.getFkTenantId();
           Set<Roles> filtered = new HashSet<>();
           for (Roles r : groups.getMappedRoles()) {
               if (r == null) continue;
               try {
                   Tenant roleTenant = r.getTenant();
                   if (roleTenant == null || roleTenant.getTenantID() == null) {
                       filtered.add(r);
                       continue;
                   }
                   String roleTenantId = roleTenant.getTenantID();
                   if (!roleTenantId.equals(groupTenantId)) {
                       log.error("createGroup: mapped role belongs to different tenant {} (expected {})", roleTenantId, groupTenantId);
                       throw new ResourceNotFoundException("Mapped role belongs to a different tenant");
                   }
                   filtered.add(r);
               } catch (Exception ex) {
                   log.warn("createGroup: unable to verify role tenant, keeping role - reason={}", ex.getMessage());
                   filtered.add(r);
               }
           }
           groups.setMappedRoles(filtered);
       }

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
        List<Groups> result = groupsRepository.findByFkTenantId(tenantId)
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

        if (existing.getIsAdmin() != null && existing.getIsAdmin() == 'Y'
                && existing.getIsDefault() != null && existing.getIsDefault() == 'Y') {
            throw new AccessDeniedException("Admin group cannot be modified");
        }

        // ---------------------------
        // UNIQUE NAME CHECK
        // ---------------------------
        if (incoming.getName() != null && !incoming.getName().equals(existing.getName())) {
            groupsRepository.findByNameAndFkTenantId(incoming.getName(), existing.getFkTenantId())
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

        if (incoming.getMappedRoles() == null) {
            existing.setMappedRoles(new HashSet<>());
        } else {
            String groupTenantId = existing.getFkTenantId();
            Set<Roles> filtered = new HashSet<>();
            for (Roles r : incoming.getMappedRoles()) {
                if (r == null) continue;
                try {
                    Tenant roleTenant = r.getTenant();
                    if (roleTenant == null || roleTenant.getTenantID() == null) {
                        filtered.add(r);
                        continue;
                    }
                    String roleTenantId = roleTenant.getTenantID();
                    if (!roleTenantId.equals(groupTenantId)) {
                        log.error("updateGroup: mapped role belongs to different tenant {} (expected {})", roleTenantId, groupTenantId);
                        throw new ResourceNotFoundException("Mapped role belongs to a different tenant");
                    }
                    filtered.add(r);
                } catch (Exception ex) {
                    log.warn("updateGroup: unable to verify role tenant, keeping role - reason={}", ex.getMessage());
                    filtered.add(r);
                }
            }
            existing.setMappedRoles(filtered);
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
        List<Groups> groups = groupsRepository.findByFkTenantId(tenantId);
        log.debug("getGroupsByTenant: found {} groups for tenantId={}", groups.size(), tenantId);
        return groups;
    }

    public boolean deleteGroupsByTenantId(String tenantID) {
        log.info("deleteGroupsByTenantId: Deleting groups for tenantId={}", tenantID);
        List<Groups> groupsToDelete = groupsRepository.findByFkTenantId(tenantID);
        groupsRepository.deleteAll(groupsToDelete);
        log.debug("deleteGroupsByTenantId: Deleted {} groups for tenantId={}", groupsToDelete.size(), tenantID);
        return true;
    }
}