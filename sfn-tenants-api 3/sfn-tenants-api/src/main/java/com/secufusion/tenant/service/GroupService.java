package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.Groups;
import com.secufusion.tenant.entity.Roles;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.GroupsRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.repository.UserRepository;
import com.secufusion.tenant.util.JwtUtl;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service responsible for managing Group lifecycle and mappings within a tenant scope.
 *
 * Responsibilities:
 * - Create or retrieve default/custom groups for a tenant.
 * - Assign roles to groups (idempotent - will not duplicate mappings).
 * - Persist users and groups when a user is assigned to a group (delegated mapping behavior).
 * - Delete groups scoped to a tenant.
 *
 * Logging:
 * - Important decision points and outcomes are logged at appropriate levels:
 *   - {@code info} for high level operations and outcomes,
 *   - {@code debug} for detailed flow and IDs,
 *   - {@code warn} for recoverable unexpected input,
 *   - {@code error} for exceptional failure conditions.
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
     * Create or return an existing default group for the provided tenant.
     *
     * Behavior:
     * - Validates that the tenant exists; throws {@link ResourceNotFoundException} if not.
     * - Attempts to find a group by name scoped to the tenant. If found, returns it unchanged.
     * - If not found, constructs a new default Group, initializes key audit fields and collections,
     *   persists it and returns the saved entity.
     *
     * Side effects:
     * - May persist a new Groups entity to the database.
     *
     * Logging:
     * - Logs start and end of operation, tenant lookup outcome, group lookup outcome and creation details.
     *
     * @param tenantId    tenant identifier used to scope the group
     * @param groupName   desired name for the default group
     * @param isAdmin     whether the created default group should be marked as admin
     * @param defaultUser identifier of the user creating or requesting the default group (audit)
     * @return existing or newly created Groups entity
     * @throws ResourceNotFoundException if the tenant cannot be found
     */
    @Transactional
    public Groups createOrGetDefaultGroup(String tenantId, String groupName, boolean isAdmin, String defaultUser) {
        log.info("createOrGetDefaultGroup: start - tenantId={} groupName={} isAdmin={} defaultUser={}",
                tenantId, groupName, isAdmin, defaultUser);

        // Validate tenant existence
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> {
                    log.error("createOrGetDefaultGroup: tenant not found - tenantId={}", tenantId);
                    return new ResourceNotFoundException("Tenant not found");
                });

        log.debug("createOrGetDefaultGroup: tenant found - tenantId={} tenantName={}",
                tenantId, tenant == null ? "<null>" : tenant.toString());

        // Try to find existing group for tenant
        Groups result = groupsRepository.findByNameAndTenantId(groupName, tenantId)
                .orElseGet(() -> {
                    log.info("createOrGetDefaultGroup: Group '{}' not found for tenantId={}. Creating new default group.",
                            groupName, tenantId);

                    Groups group = new Groups();
                    // Use characters 'Y' / 'N' to indicate admin/default as in existing model.
                    group.setIsAdmin(isAdmin ? 'Y' : 'N');
                    group.setIsDefault('Y');
                    group.setDescription(groupName + " default group");
                    group.setName(groupName);
                    group.setTenantId(tenantId);
                    group.setCreatedBy(defaultUser);
                    group.setCreatedTime(LocalDateTime.now());
                    group.setActive(true);

                    // Initialize any collections to avoid NPEs when mapping relations later.
                    group.setMappedRoles(new HashSet<>());

                    log.debug("createOrGetDefaultGroup: persisting new group - name={} tenantId={} isAdmin={}",
                            groupName, tenantId, isAdmin);
                    Groups saved = groupsRepository.save(group);
                    log.info("createOrGetDefaultGroup: created group - id={} name={} tenantId={}",
                            saved.getPkGroupId(), saved.getName(), tenantId);
                    return saved;
                });

        // If an existing group was returned, log that explicitly.
        log.debug("createOrGetDefaultGroup: end - returning group id={} name={}",
                result.getPkGroupId(), result.getName());
        return result;
    }

    /**
     * Assigns the provided role to the group if it is not already mapped.
     *
     * Behavior:
     * - Validates inputs.
     * - Ensures the group's role collection is initialized.
     * - Checks for existing mapping by role primary key to avoid duplicates.
     * - Persists the group when a new mapping is added.
     *
     * Side effects:
     * - May persist the Groups entity if the role mapping is newly added.
     *
     * Logging:
     * - Logs entry, validation failures, idempotent skips and successful mapping persistence.
     *
     * @param group group to which the role should be assigned
     * @param role  role to assign
     */
    @Transactional
    public void assignRoleToGroup(Groups group, Roles role) {
        if (group == null || role == null) {
            log.warn("assignRoleToGroup: received null parameter(s). group={} role={}", group, role);
            return;
        }

        log.debug("assignRoleToGroup: start - groupId={} groupName={} roleId={} roleName={}",
                group.getPkGroupId(), group.getName(), role.getPkRoleId(), role.getName());

        if (group.getMappedRoles() == null) {
            log.debug("assignRoleToGroup: initializing mappedRoles collection for groupId={}", group.getPkGroupId());
            group.setMappedRoles(new HashSet<>());
        }

        boolean exists = group.getMappedRoles().stream()
                .anyMatch(r -> r.getPkRoleId().equals(role.getPkRoleId()));

        if (exists) {
            log.info("assignRoleToGroup: Role '{}' (id={}) already mapped to group '{}' (id={}) - no action taken",
                    role.getName(), role.getPkRoleId(), group.getName(), group.getPkGroupId());
            return;
        }

        log.debug("assignRoleToGroup: adding role id={} to group id={}", role.getPkRoleId(), group.getPkGroupId());
        group.getMappedRoles().add(role);

        // Persist the new mapping
        Groups saved = groupsRepository.save(group);
        log.info("assignRoleToGroup: Assigned role '{}' (id={}) to group '{}' (id={}) - persisted group id={}",
                role.getName(), role.getPkRoleId(), group.getName(), group.getPkGroupId(),
                saved.getPkGroupId());
    }

    /**
     * Assigns a user to a group and persists involved entities.
     *
     * Notes:
     * - The service persists both the group and the user to ensure any pending changes are flushed.
     * - This method does not make assumptions about the specific relationship fields present on the entities;
     *   it performs saves so any bi-directional mapping handled by the entities or other layers will be persisted.
     *
     * Logging:
     * - Logs entry, validation, and the outcome of persistence operations.
     *
     * @param group target group for user assignment
     * @param user  user to assign to the group
     */
    @Transactional
    public void assignUserToGroup(Groups group, User user) {
        if (group == null || user == null) {
            log.warn("assignUserToGroup: received null parameter(s). group={} user={}", group, user);
            return;
        }

        log.debug("assignUserToGroup: start - groupId={} groupName={} userId={} userName={}",
                group.getPkGroupId(), group.getName(), user.getPkUserId(), user.getUserName());

        // Persist the group first to ensure any pending state is saved (e.g. when mapping is managed elsewhere).
        log.debug("assignUserToGroup: persisting group state for groupId={}", group.getPkGroupId());
        Groups savedGroup = groupsRepository.save(group);
        log.debug("assignUserToGroup: group persisted id={}", savedGroup.getPkGroupId());

        // Persist the user to ensure user side of relationship changes are saved.
        log.debug("assignUserToGroup: persisting user state for userId={}", user.getPkUserId());
        User savedUser = userRepository.save(user);
        log.debug("assignUserToGroup: user persisted id={}", savedUser.getPkUserId());

        log.info("assignUserToGroup: Assigned user '{}' (id={}) to group '{}' (id={}) - persisted (groupId={}, userId={})",
                user.getUserName(), user.getPkUserId(), group.getName(), group.getPkGroupId(),
                savedGroup.getPkGroupId(), savedUser.getPkUserId());
    }

    /**
     * Delete all groups for the provided tenant identifier.
     *
     * Behavior:
     * - Fetches groups scoped to the tenant and deletes them in bulk.
     * - Returns {@code true} when deletion completes successfully; {@code false} if an exception occurred.
     *
     * Logging:
     * - Logs the start, number of groups found and deleted, and any exceptions encountered.
     *
     * @param tenantID tenant identifier for which groups should be deleted
     * @return {@code true} if deletion succeeded; {@code false} if an error occurred
     */
    @Transactional
    public boolean deleteGroupsByTenantId(String tenantID) {
        log.info("deleteGroupsByTenantId: start - tenantId={}", tenantID);
        try {
            groupsRepository.deleteByTenantId(tenantID);
            return true;
        } catch (Exception ex) {
            log.error("deleteGroupsByTenantId: failed to delete groups for tenantId={} error={}", tenantID, ex.getMessage(), ex);
            return false;
        }
    }

    @Transactional
    public void assignRoleToGroupIfMissing(Groups group, Roles role) {

        if (group == null || role == null) {
            log.warn(
                    "Skipping role-group assignment due to null inputs. group={}, role={}",
                    group, role
            );
            return;
        }

        // Direct SQL insert with ON CONFLICT DO NOTHING — fully idempotent,
        // bypasses JPA collection management to avoid Hibernate duplicate insert
        // issues when multiple roles are assigned in the same session.
        groupsRepository.insertRoleMappingIfAbsent(
                group.getPkGroupId(),
                role.getPkRoleId()
        );

        log.info(
                "Role mapped to group successfully. groupId={}, roleId={}",
                group.getPkGroupId(),
                role.getPkRoleId()
        );
    }


    public void deleteGroupsByTenantIdsBatch(List<String> targetIds) {
        groupsRepository.deleteByTenantIdIn(targetIds);
        log.info("deleteGroupsByTenantIdsBatch: deleted groups for tenantIds={}", targetIds);
    }

    /**
     * Returns the default admin group for the given tenant (isAdmin='Y', isDefault='Y').
     */
    public Optional<Groups> getDefaultGroupForTenant(String tenantId) {
        return groupsRepository.findByTenantIdAndIsAdminAndIsDefault(tenantId, 'Y', 'Y');
    }
}