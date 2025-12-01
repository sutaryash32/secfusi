package com.secufusion.iam.service;

import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.RolesRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class RoleService {

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    /** Create or return existing */
    @Transactional
    public Roles createOrGetDefaultRole(String tenantId, String roleName, String description, String  adminUserId) {
        log.info("Checking role '{}' for tenantId={}", roleName, tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        return rolesRepository.findByNameAndTenant_TenantID(roleName, tenantId)
                .orElseGet(() -> {
                    log.info("Role '{}' not found. Creating new...", roleName);

                    Roles role = new Roles();
                    role.setName(roleName);
                    role.setDescription(description);
                    role.setTenant(tenant);
                    role.setCreatedBy(adminUserId);
                    role.setActive(true);
                    role.setCreatedTime(LocalDateTime.now());
                    role.setIsSuperRole('Y');
                    role.setIsDefault('Y');

                    return rolesRepository.save(role);
                });
    }

    public Roles createRoles(Roles roles) {
//        rolesRepository.existsByNameAndTenant_TenantID(roles.getName(), roles.getTenant().getTenantID())
//                .ifPresent(existingRole -> {
//                    throw new ResourceNotFoundException("Role with name '" + roles.getName() + "' already exists for this tenant.");
//                });
        return rolesRepository.save(roles);
    }

    public List<Roles> getAllRoles() {
        return rolesRepository.findAll();
    }

    public Roles getRoleById(String id) {
        return rolesRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }

    public List<RoleDropdownResponse> getRolesForDropdown() {
        List<Roles> rolesList = rolesRepository.findAll();
        return rolesList.stream()
                .filter(r -> !((r.getIsSuperRole() != null && r.getIsSuperRole() == 'Y')
                            || (r.getIsDefault()  != null && r.getIsDefault()  == 'Y')))
                .map(role -> new RoleDropdownResponse(role.getPkRoleId(), role.getName()))
                .toList();
    }
}

