package com.secufusion.iam.service;

import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.dto.RolesDto;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.RolesRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for managing Roles.
 * <p>
 * Provides creation, update and retrieval operations.
 * Methods enforce tenant scoping using information extracted from the request JWT.
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

    /**
     * Create or return existing default role for a tenant.
     *
     * @param tenantId    tenant identifier
     * @param roleName    role name
     * @param description role description
     * @param adminUserId id of admin creating the role
     * @return existing or newly created Roles entity
     */
    @Transactional
    public Roles createOrGetDefaultRole(String tenantId, String roleName, String description, String adminUserId) {
        log.info("Checking role '{}' for tenantId={}", roleName, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> {
                    log.warn("Tenant not found for tenantId={}", tenantId);
                    return new ResourceNotFoundException("Tenant not found");
                });

        return rolesRepository.findByNameAndTenant_TenantID(roleName, tenantId)
                .orElseGet(() -> {
                    log.info("Role '{}' not found for tenantId={}. Creating new...", roleName, tenantId);

                    Roles role = new Roles();
                    role.setName(roleName);
                    role.setDescription(description);
                    role.setTenant(tenant);
                    role.setCreatedBy(adminUserId);
                    role.setActive(true);
                    role.setCreatedTime(LocalDateTime.now());
                    role.setIsSuperRole('Y');
                    role.setIsDefault('Y');

                    Roles saved = rolesRepository.save(role);
                    log.info("Created role pkRoleId={} name={} for tenantId={}", saved.getPkRoleId(), saved.getName(), tenantId);
                    return saved;
                });
    }

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
//        rolesRepository.existsByNameAndTenant_TenantID(rolesDto.getName(), tenantFromRequest.getTenantID())
//                .ifPresent(existingRole -> {
//                    log.warn("Role creation failed. Role '{}' already exists for tenantId={}", rolesDto.getName(), tenantFromRequest.getTenantID());
//                    throw new ResourceNotFoundException("Role with name '" + rolesDto.getName() + "' already exists for this tenant.");
//                });

        Roles role = mapToEntity(rolesDto, tenantFromRequest);
        role.setCreatedBy(userFromRequest.getPkUserId());
        Roles saved = rolesRepository.save(role);
        log.info("Role created pkRoleId={} name={} by userId={}", saved.getPkRoleId(), saved.getName(), userFromRequest.getPkUserId());
        return saved;
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
        Roles existing = rolesRepository.findRoleAccessibleByTenant(roleId,tenantFromRequest.getTenantID());
        if (existing == null) {
            log.warn("Role not found or not accessible. roleId={} tenantId={}", roleId, tenantFromRequest.getTenantID());
            throw new ResourceNotFoundException("Role not found or not accessible for tenant.");
        }

        if (rolesDto.getName() != null && !rolesDto.getName().equals(existing.getName())) {
            rolesRepository.findByNameAndTenant_TenantID(rolesDto.getName(), existing.getTenant().getTenantID())
                    .ifPresent(conflict -> {
                        if (!conflict.getPkRoleId().equals(existing.getPkRoleId())) {
                            log.warn("Role name conflict while updating roleId={} newName={} conflictRoleId={}",
                                    existing.getPkRoleId(), rolesDto.getName(), conflict.getPkRoleId());
                            throw new ResourceNotFoundException("Role with name '" + rolesDto.getName() + "' already exists for this tenant.");
                        }
                    });
            existing.setName(rolesDto.getName());
        }

        if (rolesDto.getDescription() != null) {
            existing.setDescription(rolesDto.getDescription());
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
        role.setCreatedTime(LocalDateTime.now());
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
        return rolesRepository.findByTenant_TenantID(tenantFromRequest.getTenantID()).stream().filter(role->role.getIsSuperRole() != 'Y').toList();
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
        return rolesRepository.findRoleAccessibleByTenant(id,tenantFromRequest.getTenantID());
    }

    /**
     * Get roles for dropdown usage. Currently returns all roles (filters commented out).
     *
     * @return list of RoleDropdownResponse
     */
    public List<RoleDropdownResponse> getRolesForDropdown() {
        log.info("Fetching roles for dropdown");
        List<Roles> rolesList = rolesRepository.findAll();
        return rolesList.stream()
                //                .filter(r -> !((r.getIsSuperRole() != null && r.getIsSuperRole() == 'Y')
                //                            || (r.getIsDefault()  != null && r.getIsDefault()  == 'Y')))
                .map(role -> new RoleDropdownResponse(role.getPkRoleId(), role.getName()))
                .toList();
    }
}