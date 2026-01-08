package com.secufusion.iam.service;

import com.secufusion.iam.dto.RolesDto;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.entity.Scopes;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.RolesRepository;
import com.secufusion.iam.repository.ScopesRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.JwtUtl;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * RoleService - complete implementation summary:
 * - Manages creation, retrieval, update and activation toggling of Roles.
 * - Enforces tenant scoping using tenant/user extracted from JWT via JwtUtl.
 * - Creates default roles for tenants and assigns scopes based on role type.
 * - Persists role entities and updates scopes using repository operations.
 * - Logs key lifecycle events, decisions and failures for observability.
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
     * Create a new role for the tenant extracted from the request JWT.
     *
     * @param request  HTTP servlet request containing JWT
     * @param rolesDto DTO with role data
     * @return saved Roles entity
     */
    @Transactional
    public Roles createRoles(
            HttpServletRequest request,
            @Parameter(description = "Role payload") RolesDto rolesDto) {

        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        User userFromRequest = jwtUtl.getUserFromRequest(request);

        log.info("Attempting to create role '{}' for tenantId={}", rolesDto.getName(), tenantFromRequest.getTenantID());

        ensureRoleNameUnique(rolesDto.getName(), tenantFromRequest.getTenantID());

        Roles role = mapToEntity(rolesDto, tenantFromRequest);

        // handle scopes if provided on DTO
        if (rolesDto.getScopes() != null) {
            role.setScopes(rolesDto.getScopes());
        }

        role.setCreatedBy(userFromRequest.getPkUserId());
        Roles saved = rolesRepository.save(role);
        log.info("Role created pkRoleId={} name={} by userId={}", saved.getPkRoleId(), saved.getName(), userFromRequest.getPkUserId());
        return saved;
    }

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
     * Update an existing role accessible by tenant (includes parent tenant access).
     *
     * @param request  HTTP servlet request containing JWT
     * @param roleId   role id to update
     * @param rolesDto DTO with updatable fields
     * @return updated Roles entity
     */
    @Transactional
    public Roles updateRole(HttpServletRequest request, String roleId, RolesDto rolesDto) {

        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        User userFromRequest = jwtUtl.getUserFromRequest(request);

        log.info("Updating roleId={} requested by userId={} for tenantId={}", roleId, userFromRequest.getPkUserId(), tenantFromRequest.getTenantID());

        // findRoleAccessibleByTenant enforces access for the tenant and its parent tenants
        Roles existing = rolesRepository.findRoleAccessibleByTenant(roleId, tenantFromRequest.getTenantID());
        if (existing == null) {
            log.warn("Role not found or not accessible. roleId={} tenantId={}", roleId, tenantFromRequest.getTenantID());
            throw new ResourceNotFoundException("Role not found or not accessible for tenant.");
        }

        if (rolesDto.getName() != null && !rolesDto.getName().equals(existing.getName())) {
            String newName = rolesDto.getName();
            // Use the requesting tenant's id to enforce uniqueness within the tenant scope
            rolesRepository.findByNameAndTenant_TenantID(newName, tenantFromRequest.getTenantID())
                    .ifPresent(conflict -> {
                        if (!conflict.getPkRoleId().equals(existing.getPkRoleId())) {
                            log.warn("Role name conflict while updating roleId={} newName={} conflictRoleId={}",
                                    existing.getPkRoleId(), newName, conflict.getPkRoleId());
                            throw new ResourceNotFoundException("Role with name '" + newName + "' already exists for this tenant.");
                        }
                    });
            existing.setName(newName);
        }

        if (rolesDto.getDescription() != null) {
            existing.setDescription(rolesDto.getDescription());
        }

        // Update scopes if provided on DTO
        if (rolesDto.getScopes() != null) {
            existing.setScopes(rolesDto.getScopes());
        }

        // Preserve super/default flags; update active if provided on DTO (nullable handling)
        existing.setActive(rolesDto.getActive() != null ? rolesDto.getActive() : existing.getActive());

        existing.setUpdatedBy(userFromRequest.getPkUserId());
        existing.setUpdatedTime(LocalDateTime.now());

        Roles saved = rolesRepository.save(existing);
        log.info("Role updated pkRoleId={} name={} updatedBy={}", saved.getPkRoleId(), saved.getName(), userFromRequest.getPkUserId());
        return saved;
    }

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
     * Retrieve all roles scoped to the tenant in the request JWT.
     *
     * @param request HTTP servlet request containing JWT
     * @return list of Roles
     */
    public List<Roles> getAllRoles(HttpServletRequest request) {
        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        log.info("Fetching all roles for tenantId={}", tenantFromRequest.getTenantID());
        return rolesRepository.findByTenant_TenantID(tenantFromRequest.getTenantID()).stream().toList();
    }

    /**
     * Retrieve a single role by id if accessible by the tenant.
     *
     * @param request HTTP servlet request containing JWT
     * @param id      role id
     * @return Roles or null if not accessible (caller should handle null)
     */
    public Roles getRoleById(HttpServletRequest request, String id) {
        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        log.info("Fetching roleId={} for tenantId={}", id, tenantFromRequest.getTenantID());
        return rolesRepository.findRoleAccessibleByTenant(id, tenantFromRequest.getTenantID());
    }

    @Transactional
    public Roles updateRoleActive(HttpServletRequest request, String roleId) throws AccessDeniedException {
        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        User userFromRequest = jwtUtl.getUserFromRequest(request);

        Roles existing = rolesRepository.findRoleAccessibleByTenant(roleId, tenantFromRequest.getTenantID());
        if (existing == null) {
            log.warn("Role not found or not accessible for active toggle. roleId={} tenantId={}", roleId, tenantFromRequest.getTenantID());
            throw new ResourceNotFoundException("Role not found or not accessible for tenant.");
        }
        if(existing.getIsDefault() != null && existing.getIsDefault() == 'Y'
                && existing.getIsSuperRole() != null && existing.getIsSuperRole() == 'Y') {
            log.warn("Attempt to update on default super role denied. roleId={} tenantId={}", roleId, tenantFromRequest.getTenantID());
            throw new AccessDeniedException("Cannot update for a Default Super role.");
        }

        boolean currentlyActive = Boolean.TRUE.equals(existing.getActive());
        boolean newActive = !currentlyActive;

        log.info("Toggling active from {} to {} for roleId={} requested by userId={} for tenantId={}",
                currentlyActive, newActive, roleId, userFromRequest.getPkUserId(), tenantFromRequest.getTenantID());

        existing.setActive(newActive);
        existing.setUpdatedBy(userFromRequest.getPkUserId());
        existing.setUpdatedTime(LocalDateTime.now());

        Roles saved = rolesRepository.save(existing);
        log.info("Role active toggled pkRoleId={} active={} updatedBy={}", saved.getPkRoleId(), saved.getActive(), userFromRequest.getPkUserId());
        return saved;
    }


    private List<String> allowedTenantTypesForRole(Roles role) {

        String roleName = role.getName().toUpperCase();

        return switch (roleName) {
            case "MASTER MSSP ADMIN" -> List.of("MASTER MSSP", "MSSP");
            case "MSSP ADMIN"        -> List.of("MSSP");
            case "ENTERPRISE ADMIN"  -> List.of("ENTERPRISE");
            default -> List.of();
        };
    }

}