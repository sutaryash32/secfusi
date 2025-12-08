package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateTenantRequest;
import com.secufusion.iam.entity.Groups;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.repository.GroupsRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * DefaultTenantInitializer
 *
 * Complete implementation: Ensures the presence of a default \"master\" tenant and
 * bootstraps tenant-level artifacts required by the system:
 * - Create tenant if missing
 * - Locate or create default admin user
 * - Ensure default roles exist
 * - Ensure default admin group exists
 * - Map admin role to admin group
 * - Map default admin user to admin group
 *
 * Logs are structured per step for easier diagnostics during application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTenantInitializer {

    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final RoleService roleService;
    private final GroupService groupService;
    private final GroupsRepository groupRepository;
    private final UserRepository userRepository;

    @Value("${master.admin.email}")
    private String masterAdminEmail;

    @Value("${master.admin.username}")
    private String masterAdminUsername;

    @Value("${master.admin.firstname}")
    private String masterAdminFirstName;

    @Value("${master.admin.lastname}")
    private String masterAdminLastName;

    @Value("${master.admin.phone}")
    private String masterAdminPhone;

    @Value("${master.admin.domain}")
    private String masterTenantDomain;

    @Value("${master.admin.tenant-name}")
    private String defaultTenantName;

    /**
     * Initialize default tenant, roles, groups and default admin user mapping.
     *
     * Logging added to provide step-level, success/failure and contextual details.
     */
    public void initialize() {

        log.info("--------------------------------------------------------------");
        log.info("[INIT] Default Tenant Onboarding - BEGIN");
        log.info("--------------------------------------------------------------");

        try {
            // -----------------------------------------------------------
            // 1) FETCH OR CREATE TENANT
            // -----------------------------------------------------------
            log.info("[STEP 1] Verify tenant existence: tenantName='{}'", defaultTenantName);

            Tenant tenant = tenantRepository.findByTenantName(defaultTenantName)
                    .orElse(null);

            if (tenant == null) {
                log.info("[STEP 1] Tenant '{}' NOT found → Creating new tenant...", defaultTenantName);

                CreateTenantRequest req = new CreateTenantRequest();
                req.setTenantName(defaultTenantName);
                req.setDomain(masterTenantDomain);
                req.setRegion("GLOBAL");
                req.setTenantType("Master MSSP");
                req.setIndustry("Technology");
                req.setPhoneNo(masterAdminPhone);
                req.setBillingCycleType("Yearly");
                req.setEmail(masterAdminEmail);

                req.setAdminPhoneNumber(masterAdminPhone);
                req.setAdminFirstName(masterAdminFirstName);
                req.setAdminLastName(masterAdminLastName);
                req.setAdminUserName(masterAdminUsername);
                req.setAdminEmail(masterAdminEmail);

                // HttpServletRequest not required here; explicitly passing null.
                log.debug("[STEP 1] Prepared CreateTenantRequest: tenantName={}, domain={}, adminEmail={}",
                        req.getTenantName(), req.getDomain(), req.getAdminEmail());

                HttpServletRequest httpServletRequest = null;
                tenantService.createTenant(httpServletRequest, req);

                tenant = tenantRepository.findByTenantName(defaultTenantName)
                        .orElseThrow(() -> new RuntimeException("Tenant creation failed !"));

                log.info("[STEP 1] Tenant '{}' successfully created with ID={}",
                        defaultTenantName, tenant.getTenantID());

            } else {
                log.info("[STEP 1] Tenant '{}' already exists (ID={})",
                        defaultTenantName, tenant.getTenantID());
            }

            final String tenantId = tenant.getTenantID();

            // Attempt to locate the default generated user for this tenant.
            Optional<User> tenantUser = userRepository.findByTenant_TenantIDAndDefaultUser(tenantId, true);
            if (tenantUser.isEmpty()) {
                log.warn("[STEP 1.1] No default tenant user found for tenantId={} (expected default user). " +
                        "Subsequent operations will proceed with a null user reference where applicable.", tenantId);
            } else {
                log.debug("[STEP 1.1] Default tenant user found: username={}, pkUserId={}",
                        tenantUser.get().getUserName(), tenantUser.get().getPkUserId());
            }

            // Use nullable userId for downstream service calls
            String defaultUserId = tenantUser.map(User::getPkUserId).orElse(null);

            // -----------------------------------------------------------
            // 2) ENSURE DEFAULT ROLE EXISTS
            // -----------------------------------------------------------
            log.info("[STEP 2] Ensuring default roles for tenantId={} ...", tenantId);

            // Create or retrieve an Administrator role for the tenant.
            Roles adminRole = roleService.createOrGetMasterMsspAdminRole(
                    tenantId, defaultUserId);

            // create other default roles for MSSP tenant and Enterprise tenants
            Roles msspRole = roleService.createOrGetMsspAdminRole(tenantId, defaultUserId);
            Roles enterpriseRole = roleService.createOrGetEnterpriseAdminRole(tenantId, defaultUserId);

            log.info("[STEP 2] Default roles verified. masterAdminRoleId={}, msspRoleId={}, enterpriseRoleId={}",
                    adminRole != null ? adminRole.getPkRoleId() : "<null>",
                    msspRole != null ? msspRole.getPkRoleId() : "<null>",
                    enterpriseRole != null ? enterpriseRole.getPkRoleId() : "<null>");

            // -----------------------------------------------------------
            // 3) ENSURE DEFAULT GROUP EXISTS
            // -----------------------------------------------------------
            log.info("[STEP 3] Checking default Admin group existence for tenantId={}", tenantId);

            String expectedGroupName = defaultTenantName + "_Admin";

            Groups adminGroup = groupRepository
                    .findByTenantIdAndIsAdminAndIsDefault(tenantId, 'Y', 'Y')
                    .orElse(null);

            if (adminGroup == null) {
                log.warn("[STEP 3] No default admin group found → Creating '{}'", expectedGroupName);

                adminGroup = groupService.createOrGetDefaultGroup(
                        tenantId, expectedGroupName, true, defaultUserId);

                // Ensure persisted flag is set and save.
                adminGroup.setIsDefault('Y');
                groupRepository.save(adminGroup);

                log.info("[STEP 3] Default admin group '{}' created (ID={})",
                        expectedGroupName, adminGroup.getPkGroupId());
            } else {
                log.info("[STEP 3] Default admin group already exists (ID={})",
                        adminGroup.getPkGroupId());
            }

            // -----------------------------------------------------------
            // 4) ASSIGN ROLES → GROUP
            // -----------------------------------------------------------
            log.info("[STEP 4] Ensure Admin role mapped to default admin group (groupId={}, roleId={})...",
                    adminGroup != null ? adminGroup.getPkGroupId() : "<null>",
                    adminRole != null ? adminRole.getPkRoleId() : "<null>");

            try {
                groupService.assignRoleToGroup(adminGroup, adminRole);
                log.info("[STEP 4] Admin role mapping complete.");
            } catch (Exception e) {
                log.error("[STEP 4] Failed to assign role to group: {}", e.getMessage(), e);
            }

            // -----------------------------------------------------------
            // 5) ASSIGN DEFAULT ADMIN USER → GROUP
            // -----------------------------------------------------------
            log.info("[STEP 5] Checking for default admin user under tenant...");

            // Prefer user object from tenant entity if present; fallback to repository lookup above.
            User defaultAdminUser = tenant.getUsers()
                    .stream()
                    .filter(User::isDefaultUser)
                    .findFirst()
                    .orElse(tenantUser.orElse(null));

            if (defaultAdminUser != null) {
                log.info("[STEP 5] Default admin user found (username={}) → mapping to group...",
                        defaultAdminUser.getUserName());

                try {
                    Set<Groups> groupsToAssign = new HashSet<>();
                    groupsToAssign.add(adminGroup);
                    defaultAdminUser.setMappedGroups(groupsToAssign);
                    userRepository.save(defaultAdminUser);
                    log.info("[STEP 5] Default admin user mapped to group '{}'", expectedGroupName);
                } catch (Exception e) {
                    log.error("[STEP 5] Failed to assign user to group: {}", e.getMessage(), e);
                }
            } else {
                log.warn("[STEP 5] No default admin user found for tenant '{}'. Skipping user->group mapping.", defaultTenantName);
            }

            // -----------------------------------------------------------
            log.info("--------------------------------------------------------------");
            log.info("[INIT] Default Tenant Onboarding - COMPLETED SUCCESSFULLY");
            log.info("--------------------------------------------------------------");

        } catch (Exception ex) {
            // Log full stacktrace and message for diagnostics.
            log.error("--------------------------------------------------------------");
            log.error("[INIT ERROR] Default Onboarding FAILED: {}", ex.getMessage(), ex);
            log.error("--------------------------------------------------------------");
        }
    }
}