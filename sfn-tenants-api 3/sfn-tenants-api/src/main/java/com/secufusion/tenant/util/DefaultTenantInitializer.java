package com.secufusion.tenant.util;

import com.secufusion.tenant.dto.CreateTenantRequest;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.GroupsRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.repository.UserRepository;
import com.secufusion.tenant.service.*;
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
 * Bootstraps the master tenant in a fully resumable & idempotent way.
 * Safe to run on every application startup.
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
    private final BrowserPolicyService browserPolicyService;
    private final NetworkPolicyService networkPolicyService;
    private final ExtensionPolicyService extensionPolicyService;
    private final SmtpConfigService smtpConfigService;

    private final KeycloakAdminUtil keycloakAdminUtil;

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

    @Value("${mail.smtp.host}")
    private String smtpHost;

    @Value("${mail.smtp.port}")
    private int smtpPort;

    @Value("${mail.smtp.auth}")
    private boolean smtpAuth;

    @Value("${mail.smtp.starttls}")
    private boolean smtpStartTls;

    @Value("${mail.smtp.username}")
    private String smtpUsername;

    @Value("${mail.smtp.password}")
    private String smtpPassword;

    @Value("${mail.smtp.mail}")
    private String smtpMail;

    /**
     * Initialize default tenant, roles, groups and admin mappings.
     * Fully safe for retries and partial failures.
     */
    public void initialize() {

        log.info("==============================================================");
        log.info("[INIT] Default Tenant Bootstrap - START");
        log.info("==============================================================");

        try {
            // -----------------------------------------------------------
            // STEP 1: ENSURE TENANT EXISTS (RESUMABLE)
            // -----------------------------------------------------------
            log.info("[STEP 1] Ensuring tenant '{}' exists (resumable)", defaultTenantName);

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

            keycloakAdminUtil.disableMasterIdpRedirector();

            // System-level tenant creation (no HttpServletRequest)
            tenantService.createTenantSystem(req);

            // Update master-hub IdP credentials for default tenant (if it's Azure SSO)
            try {
                Tenant defaultTenant = tenantRepository.findByTenantName(defaultTenantName).orElse(null);
                if (defaultTenant != null && defaultTenant.getRealmName() != null) {
                    // Get broker client credentials from Keycloak
                    String brokerClientId = "broker-for-" + defaultTenant.getRealmName();
                    if (keycloakAdminUtil.clientExists("master", brokerClientId)) {
                        org.keycloak.representations.idm.ClientRepresentation brokerClient =
                                keycloakAdminUtil.getClientWithSecret("master", brokerClientId);

                        // Update master-hub IdP with current broker client credentials
                        if (keycloakAdminUtil.idpExists(defaultTenant.getRealmName(), "master-hub")) {
                            keycloakAdminUtil.updateMasterHubIdpWithCredentials(
                                    defaultTenant.getRealmName(),
                                    brokerClient.getClientId(),
                                    brokerClient.getSecret()
                            );
                            log.info("✅ Updated master-hub IdP credentials for default tenant on startup");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to update master-hub IdP credentials on startup: {}", e.getMessage());
                // Non-fatal - continue with initialization
            }

            // Create default policies (global, tenant_id = NULL)
            try {
                browserPolicyService.createdDefaultPolicyIfNotExists();
                log.info("Default browser policy ensured");
            } catch (Exception e) {
                log.error("Failed to create default browser policy", e);
            }

            try {
                networkPolicyService.createDefaultPolicyIfNotExists();
                log.info("Default network policy ensured");
            } catch (Exception e) {
                log.error("Failed to create default network policy", e);
            }

            try {
                extensionPolicyService.createDefaultPolicyIfNotExists();
                log.info("Default extension policy ensured");
            } catch (Exception e) {
                log.error("Failed to create default extension policy", e);
            }

            SmtpConfig defaultSmtpConfig = new SmtpConfig();
            defaultSmtpConfig.setHost(smtpHost);
            defaultSmtpConfig.setPort(smtpPort);
            defaultSmtpConfig.setAuth(String.valueOf(smtpAuth));
            defaultSmtpConfig.setStarttls(String.valueOf(smtpStartTls));
            defaultSmtpConfig.setUsername(smtpUsername);
            defaultSmtpConfig.setPassword(smtpPassword);
            defaultSmtpConfig.setFromEmail(smtpMail);
            defaultSmtpConfig.setFromName(smtpMail);
            try {
                smtpConfigService.createDefaultSmtpConfig(defaultSmtpConfig);
                log.info("Default SMTP config ensured");
            } catch (Exception e) {
                log.error("Failed to create default SMTP config", e);
            }

            Tenant tenant = tenantRepository.findByTenantName(defaultTenantName)
                    .orElseThrow(() -> new IllegalStateException("Master tenant creation failed"));

            log.info("[STEP 1] Tenant ready. tenantId={}, status={}",
                    tenant.getTenantID(), tenant.getStatus());

            String tenantId = tenant.getTenantID();

            // -----------------------------------------------------------
            // STEP 1.1: FETCH DEFAULT ADMIN USER (OPTIONAL)
            // -----------------------------------------------------------
            Optional<User> defaultUserOpt =
                    userRepository.findByTenant_TenantIDAndDefaultUser(tenantId, true);

            String defaultUserId =
                    defaultUserOpt.map(User::getPkUserId).orElse(null);

            if (defaultUserOpt.isPresent()) {
                log.info("[STEP 1.1] Default admin user found: username={}",
                        defaultUserOpt.get().getUserName());
            } else {
                log.warn("[STEP 1.1] Default admin user not found yet (will retry later)");
            }

            // -----------------------------------------------------------
            // STEP 2: ENSURE ROLES
            // -----------------------------------------------------------
            log.info("[STEP 2] Ensuring default roles for tenantId={}", tenantId);

            Roles masterAdminRole =
                    roleService.createOrGetMasterMsspAdminRole(tenantId, defaultUserId);
            Roles msspRole =
                    roleService.createOrGetMsspAdminRole(tenantId, defaultUserId);
            Roles enterpriseRole =
                    roleService.createOrGetEnterpriseAdminRole(tenantId, defaultUserId);

            log.info("[STEP 2] Roles ensured. master={}, mssp={}, enterprise={}",
                    masterAdminRole.getPkRoleId(),
                    msspRole.getPkRoleId(),
                    enterpriseRole.getPkRoleId());

            // -----------------------------------------------------------
            // STEP 3: ENSURE DEFAULT ADMIN GROUP
            // -----------------------------------------------------------
            log.info("[STEP 3] Ensuring default admin group");

            String adminGroupName = defaultTenantName + "_Admin";

            Groups adminGroup = groupRepository
                    .findByTenantIdAndIsAdminAndIsDefault(tenantId, 'Y', 'Y')
                    .orElseGet(() -> {
                        log.info("[STEP 3] Creating default admin group '{}'", adminGroupName);
                        Groups g = groupService.createOrGetDefaultGroup(
                                tenantId, adminGroupName, true, defaultUserId);
                        g.setIsDefault('Y');
                        return groupRepository.save(g);
                    });

            log.info("[STEP 3] Admin group ready. groupId={}",
                    adminGroup.getPkGroupId());

            // -----------------------------------------------------------
            // STEP 4: MAP ROLE → GROUP (IDEMPOTENT)
            // -----------------------------------------------------------
            log.info("[STEP 4] Ensuring role mapped to admin group");

            groupService.assignRoleToGroupIfMissing(adminGroup, masterAdminRole);

            log.info("[STEP 4] Role mapped to group successfully");

            // -----------------------------------------------------------
            // STEP 5: MAP ADMIN USER → GROUP (MERGE SAFE)
            // -----------------------------------------------------------
            log.info("[STEP 5] Ensuring admin user mapped to admin group");

            User adminUser = tenant.getUsers()
                    .stream()
                    .filter(User::isDefaultUser)
                    .findFirst()
                    .orElse(defaultUserOpt.orElse(null));

            if (adminUser != null) {
                Set<Groups> groups =
                        Optional.ofNullable(adminUser.getMappedGroups())
                                .orElse(new HashSet<>());

                if (!groups.contains(adminGroup)) {
                    groups.add(adminGroup);
                    adminUser.setMappedGroups(groups);
                    userRepository.save(adminUser);
                    log.info("[STEP 5] Admin user mapped to group '{}'", adminGroupName);
                } else {
                    log.info("[STEP 5] Admin user already mapped to admin group");
                }
            } else {
                log.warn("[STEP 5] Admin user not yet available; mapping will occur on retry");
            }

            log.info("==============================================================");
            log.info("[INIT] Default Tenant Bootstrap - COMPLETED SUCCESSFULLY");
            log.info("==============================================================");

        } catch (Exception e) {
            log.error("==============================================================");
            log.error("[INIT ERROR] Default Tenant Bootstrap FAILED", e);
            log.error("==============================================================");
        }
    }
}
