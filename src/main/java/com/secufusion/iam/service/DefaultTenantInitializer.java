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

import java.util.Optional;

/**
 * DefaultTenantInitializer
 * <p>
 * Responsible for ensuring a default "master" tenant exists along with
 * its default roles, groups and default admin user mappings.
 * <p>
 * This class is executed during application initialization to bootstrap
 * tenant-related artifacts required by the system.
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
     * <p>
     * Added logging to aid debugging and comments to explain each step.
     */
    public void initialize() {

        log.info("==============================================================");
        log.info(">>> [INIT] Starting Default Tenant Onboarding Process");
        log.info("==============================================================");

        try {
            // -----------------------------------------------------------
            // 1) FETCH OR CREATE TENANT
            // -----------------------------------------------------------
            log.info("[1] Checking if tenant '{}' exists...", defaultTenantName);

            Tenant tenant = tenantRepository.findByTenantName(defaultTenantName)
                    .orElse(null);

            if (tenant == null) {
                log.info("[1] Tenant '{}' NOT found → Creating new tenant...", defaultTenantName);

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
                log.debug("[1] Prepared CreateTenantRequest: tenantName={}, domain={}",
                        req.getTenantName(), req.getDomain());

                HttpServletRequest httpServletRequest = null;
                tenantService.createTenant(httpServletRequest, req);

                tenant = tenantRepository.findByTenantName(defaultTenantName)
                        .orElseThrow(() -> new RuntimeException("Tenant creation failed !"));

                log.info("[1] Tenant '{}' successfully created with ID={}",
                        defaultTenantName, tenant.getTenantID());

            } else {
                log.info("[1] Tenant '{}' already exists (ID={})",
                        defaultTenantName, tenant.getTenantID());
            }

            final String tenantId = tenant.getTenantID();

            // Attempt to locate the default generated user for this tenant.
            Optional<User> tenantUser = userRepository.findByTenant_TenantIDAndDefaultUser(tenantId, true);
            if (tenantUser.isEmpty()) {
                // Log but continue: some services accept null as creator/owner id.
                log.warn("[1.1] No default tenant user found for tenantId={} (expected default user). " +
                        "Subsequent operations will proceed with a null user reference where applicable.", tenantId);
            } else {
                log.debug("[1.1] Default tenant user found: username={}, pkUserId={}",
                        tenantUser.get().getUserName(), tenantUser.get().getPkUserId());
            }

            // Use nullable userId for downstream service calls
            String defaultUserId = tenantUser.map(User::getPkUserId).orElse(null);

            // -----------------------------------------------------------
            // 2) ENSURE DEFAULT ROLE EXISTS
            // -----------------------------------------------------------
            log.info("[2] Ensuring default roles for tenantId={} ...", tenantId);

            // Create or retrieve an Administrator role for the tenant.
            Roles adminRole = roleService.createOrGetDefaultRole(
                    tenantId, defaultTenantName + "_Admin", "Administrator role", defaultUserId);

            log.info("[2] Default roles verified (Admin) - roleId={}", adminRole != null ? adminRole.getPkRoleId() : "<null>");

            // -----------------------------------------------------------
            // 3) ENSURE DEFAULT GROUP EXISTS
            // -----------------------------------------------------------
            log.info("[3] Checking default Admin group existence...");

            String expectedGroupName = defaultTenantName + "_Admin";

            Groups adminGroup = groupRepository
                    .findByTenantIdAndIsAdminAndIsDefault(tenantId, 'Y', 'Y')
                    .orElse(null);

            if (adminGroup == null) {
                log.warn("[3] No default admin group found → Creating '{}'", expectedGroupName);

                adminGroup = groupService.createOrGetDefaultGroup(
                        tenantId, expectedGroupName, true, defaultUserId);

                // Ensure persisted flag is set and save.
                adminGroup.setIsDefault('Y');
                groupRepository.save(adminGroup);

                log.info("[3] Default admin group '{}' created (ID={})",
                        expectedGroupName, adminGroup.getPkGroupId());
            } else {
                log.info("[3] Default admin group already exists (ID={})",
                        adminGroup.getPkGroupId());
            }

            // -----------------------------------------------------------
            // 4) ASSIGN ROLES → GROUP
            // -----------------------------------------------------------
            log.info("[4] Ensuring Admin role is mapped to default admin group (groupId={}, roleId={})...",
                    adminGroup != null ? adminGroup.getPkGroupId() : "<null>",
                    adminRole != null ? adminRole.getPkRoleId() : "<null>");

            groupService.assignRoleToGroup(adminGroup, adminRole);

            log.info("[4] Admin role mapping complete.");

            // -----------------------------------------------------------
            // 5) ASSIGN DEFAULT ADMIN USER → GROUP
            // -----------------------------------------------------------
            log.info("[5] Checking for default admin user under tenant...");

            // Prefer user object from tenant entity if present; fallback to repository lookup above.
            User defaultAdminUser = tenant.getUsers()
                    .stream()
                    .filter(User::isDefaultUser)
                    .findFirst()
                    .orElse(tenantUser.orElse(null));

            if (defaultAdminUser != null) {
                log.info("[5] Default admin user found (username={}) → mapping to group...",
                        defaultAdminUser.getUserName());

                groupService.assignUserToGroup(adminGroup, defaultAdminUser);

                log.info("[5] Default admin user mapped to group '{}'", expectedGroupName);
            } else {
                log.warn("[5] No default admin user found for tenant '{}'. Skipping user->group mapping.", defaultTenantName);
            }

            // -----------------------------------------------------------
            log.info("==============================================================");
            log.info(">>> [INIT] Default Tenant Onboarding Completed Successfully");
            log.info("==============================================================");

        } catch (Exception ex) {
            // Log full stacktrace and message for diagnostics.
            log.error("==============================================================");
            log.error(">>> [INIT ERROR] Default Onboarding FAILED: {}", ex.getMessage(), ex);
            log.error("==============================================================");
        }
    }
}