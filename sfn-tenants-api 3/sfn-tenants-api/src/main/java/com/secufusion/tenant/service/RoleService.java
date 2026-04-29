package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.RolesDto;
import com.secufusion.tenant.entity.Roles;
import com.secufusion.tenant.entity.Scopes;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.RolesRepository;
import com.secufusion.tenant.repository.ScopesRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.repository.UserRepository;
import com.secufusion.tenant.util.JwtUtl;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service responsible for managing Roles.
 * <p>
 * Responsibilities:
 * - Create or return tenant default roles (master/mssp/enterprise admin).
 * - Create arbitrary tenant roles from incoming DTOs (scoped to tenant from JWT).
 * - Ensure appropriate scopes are assigned based on role type.
 * - Provide defensive logging and avoid blocking onboarding on non-fatal failures.
 * <p>
 * Notes:
 * - Methods that modify state are annotated with {@code @Transactional}.
 * - This service expects repositories and JWT utilities to be available via Spring DI.
 */
@Service
@Slf4j
public class RoleService {

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtl jwtUtl;

    @Autowired
    private ScopesRepository scopesRepository;

    /**
     * Ensure there is a default "MASTER MSSP ADMIN" role for the tenant, returning the
     * existing role when present or creating it otherwise.
     *
     * @param tenantId    tenant identifier used to look up tenant record
     * @param adminUserId identifier of the admin performing creation (for audit fields)
     * @return existing or newly created Roles entity; returns {@code null} on failure (non-blocking)
     */
    @Transactional
    public Roles createOrGetMasterMsspAdminRole(String tenantId, String adminUserId) {
        return createOrGetDefaultAdminRole(
                tenantId,
                adminUserId,
                "MASTER MSSP ADMIN",
                "Master MSSP Administrator Role"
        );
    }

    /**
     * Ensure there is a default "MSSP ADMIN" role for the tenant.
     */
    @Transactional
    public Roles createOrGetMsspAdminRole(String tenantId, String adminUserId) {
        return createOrGetDefaultAdminRole(
                tenantId,
                adminUserId,
                "MSSP ADMIN",
                "MSSP Administrator Role"
        );
    }

    /**
     * Ensure there is a default "ENTERPRISE ADMIN" role for the tenant.
     */
    @Transactional
    public Roles createOrGetEnterpriseAdminRole(String tenantId, String adminUserId) {
        return createOrGetDefaultAdminRole(
                tenantId,
                adminUserId,
                "ENTERPRISE ADMIN",
                "Enterprise Administrator Role"
        );
    }

    /**
     * Remove ENTERPRISE ADMIN role from a user's groups when selfManaged is disabled.
     * Finds all groups the user belongs to that carry the ENTERPRISE ADMIN role
     * and removes the role from those groups (or removes the user from those groups
     * if the group was created solely for the enterprise role).
     * Non-destructive — only touches ENTERPRISE ADMIN role assignment.
     */
    @Transactional
    public void removeEnterpriseAdminRoleFromUser(String tenantId, String userId) {
        log.info("removeEnterpriseAdminRoleFromUser: tenantId={} userId={}", tenantId, userId);
        try {
            Optional<Roles> enterpriseRoleOpt = rolesRepository
                    .findByNameAndIsDefaultAndIsSuperRole("ENTERPRISE ADMIN", 'Y', 'Y');

            if (enterpriseRoleOpt.isEmpty()) {
                log.info("ENTERPRISE ADMIN role not found, nothing to remove for tenantId={}", tenantId);
                return;
            }

            Roles enterpriseRole = enterpriseRoleOpt.get();

            // Find all groups that carry the ENTERPRISE ADMIN role for this tenant
            // and remove the role from those groups
            com.secufusion.tenant.entity.User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            Optional.ofNullable(user.getMappedGroups())
                    .orElse(new HashSet<>())
                    .forEach(group -> {
                        if (group.getMappedRoles() != null
                                && group.getMappedRoles().removeIf(r ->
                                        r.getPkRoleId().equals(enterpriseRole.getPkRoleId()))) {
                            log.info("Removed ENTERPRISE ADMIN role from groupId={}", group.getPkGroupId());
                        }
                    });

            userRepository.save(user);
        } catch (Exception e) {
            log.warn("removeEnterpriseAdminRoleFromUser failed for userId={}: {}", userId, e.getMessage());
        }
    }

    public Roles resolveAdminRoleForTenant(
            Tenant tenant,
            String adminUserId
    ) {
        return resolveAdminRolesForTenant(tenant, adminUserId).get(0);
    }

    /**
     * Resolve all admin roles for a tenant, supporting selfManaged dual-role assignment.
     * <p>
     * For selfManaged MSSP/Master MSSP tenants, returns both the primary management role
     * AND the ENTERPRISE ADMIN role, so the admin can manage sub-tenants AND their own users.
     *
     * @param tenant      the tenant being provisioned
     * @param adminUserId the admin user id (for audit fields)
     * @return list of roles to assign; first element is the primary role
     */
    @Transactional
    public List<Roles> resolveAdminRolesForTenant(
            Tenant tenant,
            String adminUserId
    ) {
        String type = Optional.ofNullable(tenant.getTenantType())
                .map(String::trim)
                .map(String::toLowerCase)
                .orElse("");

        boolean selfManaged = Boolean.TRUE.equals(tenant.getSelfManaged());
        List<Roles> roles = new ArrayList<>();

        switch (type) {
            case "master mssp", "master_mssp", "mastermssp" -> {
                roles.add(createOrGetMasterMsspAdminRole(tenant.getTenantID(), adminUserId));
                if (selfManaged) {
                    // selfManaged Master MSSP also manages their own users → Enterprise role
                    roles.add(createOrGetEnterpriseAdminRole(tenant.getTenantID(), adminUserId));
                }
            }
            case "mssp" -> {
                roles.add(createOrGetMsspAdminRole(tenant.getTenantID(), adminUserId));
                if (selfManaged) {
                    // selfManaged MSSP also manages their own users → Enterprise role
                    roles.add(createOrGetEnterpriseAdminRole(tenant.getTenantID(), adminUserId));
                }
            }
            case "enterprise" ->
                roles.add(createOrGetEnterpriseAdminRole(tenant.getTenantID(), adminUserId));
            default -> throw new IllegalStateException("Unexpected tenant type: " + type);
        }

        return roles;
    }

    /**
     * Centralized implementation to create or return a default admin role.
     * <p>
     * Behaviour:
     * - Attempts to resolve the tenant by {@code tenantId} and aborts if missing.
     * - Searches for an existing default, super role by name. If found, ensures scopes are present.
     * - If not found, creates the role, persists it and assigns scopes based on role type.
     * <p>
     * This method intentionally swallows exceptions and returns {@code null} on error to avoid
     * failing tenant onboarding flows; failures are logged.
     */
    @Transactional
    private Roles createOrGetDefaultAdminRole(
            String tenantId,
            String adminUserId,
            String roleName,
            String description
    ) {
        log.info("Checking for role '{}'", roleName);

        try {
            // Resolve tenant; throw if not found to avoid creating orphaned roles.
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

            // Note: default/super role lookup is global in the current implementation.
            // Uses JOIN FETCH to eagerly load scopes and avoid LazyInitializationException.
            Optional<Roles> existing =
                    rolesRepository.findByNameAndIsDefaultAndIsSuperRoleWithScopes(roleName, 'Y', 'Y');

            if (existing.isPresent()) {
                Roles existingRole = existing.get();
                log.info("Role '{}' already exists with roleId={}", roleName, existingRole.getPkRoleId());

                // Ensure required scopes are present; non-fatal on failure.
                try {
                    assignScopesByRoleType(existingRole);
                } catch (Exception ex) {
                    log.warn("Failed to ensure scopes for role {}: {}", existingRole.getPkRoleId(), ex.getMessage(), ex);
                }

                // Reload to return the freshest entity state, fallback to the in-memory instance.
                return rolesRepository.findById(existingRole.getPkRoleId()).orElse(existingRole);
            }

            log.warn("Role '{}' not found → Creating...", roleName);

            Roles role = new Roles();
            role.setName(roleName);
            role.setDescription(description);
            role.setTenant(tenant);
            role.setCreatedBy(adminUserId);
            role.setActive(true);
            role.setCreatedTime(LocalDateTime.now());
            role.setIsSuperRole('Y');
            role.setIsDefault('Y');

            Roles savedRole = rolesRepository.save(role);
            log.info("Created new role '{}' with id={}", roleName, savedRole.getPkRoleId());

            // Assign scopes appropriate for role type; this method persists role if new scopes added.
            assignScopesByRoleType(savedRole);

            return savedRole;

        } catch (Exception e) {
            // Catch-all to avoid blocking onboarding; ensure clear logging for diagnosis.
            log.error("Failed to create or fetch '{}' role: {}", roleName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Create a tenant-scoped role from the provided DTO. The tenant and user are extracted
     * from the incoming HTTP request's JWT via {@code JwtUtl}.
     *
     * @param request  incoming request with authorization JWT
     * @param rolesDto incoming role payload
     * @return saved Roles entity
     */
    @Transactional
    public Roles createRoles(
            HttpServletRequest request,
            @Parameter(description = "Role payload") RolesDto rolesDto) {

        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        User userFromRequest = jwtUtl.getUserFromRequest(request);

        log.info("Attempting to create role '{}' for tenantId={}", rolesDto.getName(), tenantFromRequest.getTenantID());

        // Validate uniqueness within this tenant
        ensureRoleNameUnique(rolesDto.getName(), tenantFromRequest.getTenantID());

        Roles role = mapToEntity(rolesDto, tenantFromRequest);

        // If DTO provided scopes, accept them; otherwise assign scopes later if desired.
        if (rolesDto.getScopes() != null) {
            role.setScopes(rolesDto.getScopes());
        }

        role.setCreatedBy(userFromRequest.getPkUserId());

        Roles saved = rolesRepository.save(role);
        log.info("Role created pkRoleId={} name={} by userId={}", saved.getPkRoleId(), saved.getName(), userFromRequest.getPkUserId());
        return saved;
    }

    /**
     * Ensures role name is unique per tenant. If conflict detected, throws
     * {@code ResourceNotFoundException} with a conflict message (existing behaviour).
     *
     * @param roleName candidate role name
     * @param tenantId tenant identifier
     */
    private void ensureRoleNameUnique(String roleName, String tenantId) {
        if (roleName == null) {
            return;
        }
        rolesRepository.findByNameAndTenant_TenantID(roleName, tenantId)
                .ifPresent(conflict -> {
                    log.warn("Role creation conflict for tenantId={} name={} conflictRoleId={}", tenantId, roleName, conflict.getPkRoleId());
                    throw new ResourceNotFoundException("Role with name '" + roleName + "' already exists for this tenant.");
                });
    }

    /**
     * Maps a RolesDto to a Roles entity with common default values set.
     *
     * @param dto    incoming DTO
     * @param tenant resolved tenant entity
     * @return mapped Roles entity (not persisted)
     */
    private Roles mapToEntity(RolesDto dto, Tenant tenant) {
        Roles role = new Roles();
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        role.setTenant(tenant);
        role.setActive(true);
        role.setCreatedTime(LocalDateTime.now());
        role.setIsSuperRole('N');
        role.setIsDefault('N');
        return role;
    }

    /**
     * Assigns scopes to the given role based on its type/name.
     * <p>
     * This method:
     * - Resolves tenant types allowed for the role.
     * - Queries the {@code ScopesRepository} for matching scopes.
     * - Adds any new scopes to the role and persists the role if changes were made.
     * <p>
     * The method is defensive: if no mappings/scopes are found it logs and returns quietly.
     *
     * @param role role to ensure scopes for
     */
    @Transactional
    public void assignScopesByRoleType(Roles role) {

        List<String> allowedTenantTypes = allowedTenantTypesForRole(role);

        if (allowedTenantTypes.isEmpty()) {
            log.warn("No tenant types mapped for role '{}'", role.getName());
            return;
        }

        log.info("Assigning scopes for role='{}' tenantTypes={}", role.getName(), allowedTenantTypes);

        // Ensure tenant type strings are upper-case for repository querying
        List<Scopes> scopes =
                scopesRepository.findByUserTypes(
                        allowedTenantTypes
                                .stream()
                                .map(String::toUpperCase)
                                .toList()
                );

        if (scopes.isEmpty()) {
            log.warn("No scopes found for role='{}'", role.getName());
            return;
        }

        // Prevent duplicate mappings by using a Set
        Set<Scopes> existingScopes = Optional.ofNullable(role.getScopes()).orElse(new HashSet<>());

        int before = existingScopes.size();
        existingScopes.addAll(scopes);

        if (existingScopes.size() > before) {
            // Persist role only if new scopes were added
            role.setScopes(existingScopes);
            rolesRepository.save(role);
            log.info("Assigned {} new scopes to role '{}'", existingScopes.size() - before, role.getName());
        } else {
            log.info("Role '{}' already has all required scopes", role.getName());
        }
    }

    /**
     * Map role name to allowed tenant types used when resolving scopes.
     * <p>
     * The mapping is uppercase and exact-match based on the role name.
     *
     * @param role role whose allowed tenant types are required
     * @return list of tenant type strings or empty list if none
     */
    private List<String> allowedTenantTypesForRole(Roles role) {
        String roleName = role.getName() == null ? "" : role.getName().toUpperCase();

        return switch (roleName) {
            case "MASTER MSSP ADMIN" -> List.of("MASTER MSSP", "MSSP");
            case "MSSP ADMIN" -> List.of("MSSP");
            case "ENTERPRISE ADMIN" -> List.of("ENTERPRISE");
            default -> List.of();
        };
    }

    @Transactional
    public void deleteRolesByTenantId(String tenantId) {
        rolesRepository.deleteByTenant_TenantID(tenantId);
        log.info("Deleted roles for tenantId={}", tenantId);
    }


    public void deleteRolesByTenantIdsBatch(List<String> targetIds) {
        rolesRepository.deleteByTenant_TenantIDIn(targetIds);
        log.info("Deleted roles for tenantIds={}", targetIds);
    }
}