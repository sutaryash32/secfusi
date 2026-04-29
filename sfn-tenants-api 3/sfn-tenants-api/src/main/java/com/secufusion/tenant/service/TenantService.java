package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.exception.*;
import com.secufusion.tenant.repository.*;
import com.secufusion.tenant.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TenantService
 * <p>
 * Responsible for complete tenant lifecycle management:
 * - Tenant creation (resumable, idempotent)
 * - Keycloak realm/client/user provisioning
 * - SSO configuration persistence
 * - Tenant activation, suspension, deletion
 * - Tenant hierarchy access control
 * <p>
 * ⚠ This service assumes Keycloak as the identity provider.
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthProviderConfigRepository authProviderConfigRepository;

    @Autowired
    private KeycloakAdminUtil kcUtil;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JwtUtl jwtUtl;

    @Autowired
    private SmtpConfigService smtpConfigService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ExtensionApiKeyRepository apiKeyRepository;

    @Autowired
    private PolicyAssignmentRepository policyAssignmentRepository;

    @Autowired
    private EventsGroupService eventsGroupService;

    @Autowired
    private BrowserPolicyService browserPolicyService;

    @Autowired
    private NetworkPolicyService networkPolicyService;

    @Autowired
    private ExtensionPolicyService extensionPolicyService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("tenantProvisioningExecutor")
    private java.util.concurrent.Executor tenantProvisioningExecutor;

    private final Map<String, String> tenantIdApiKeys = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * In-flight creation lock keyed by normalized tenant name.
     * Prevents two concurrent requests from creating the same tenant simultaneously.
     * Value is a timestamp string for debugging; presence means "in progress".
     */
    private final java.util.concurrent.ConcurrentHashMap<String, String> creationLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Self-reference injected via Spring so that intra-class calls to @Transactional(REQUIRES_NEW)
     * helpers go through the proxy and actually open a new transaction.
     */
    @Autowired
    @Lazy
    private TenantService self;

    @Value("${keycloak.admin.server-url}")
    private String baseUrl;

    @Value("${domain.extension}")
    private String extension;

    @Value("${azure.mismatch.validation}")
    private Boolean strictValidation;

    @Value("${azure.sso.client-id}")
    private String masterAzureClientId;

    @Value("${azure.sso.client-secret}")
    private String masterAzureClientSecret;

    @Value("${azure.sso.alias}")
    private String masterAzureAlias;

    @PostConstruct
    void validateConfiguration() {
        if (extension == null || extension.isBlank()) {
            throw new IllegalStateException(
                    "Property 'domain.extension' must be configured (e.g., .motivitylabs.net). "
                    + "Tenant domain normalization cannot function without it."
            );
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Property 'keycloak.admin.server-url' must be configured. "
                    + "Tenant provisioning requires a valid Keycloak server URL."
            );
        }
        log.info("TenantService configured: domain.extension={}, keycloak.server-url={}", extension, baseUrl);
    }

    public TenantResponse createTenantSystem(CreateTenantRequest req) {
        log.info("System-level tenant creation invoked for tenantName={}", req.getTenantName());

        Optional<Tenant> existingOpt =
                tenantRepository.findByTenantNameWithUsers(req.getTenantName().toLowerCase());

        // 🔑 SYSTEM RULE: ACTIVE tenant is OK → just return
        if (existingOpt.isPresent()
                && "ACTIVE".equalsIgnoreCase(existingOpt.get().getStatus())) {

            log.info("Tenant '{}' already ACTIVE. System call returning existing tenant.",
                    req.getTenantName());

            return buildResponse(existingOpt.get());
        }

        // System-level uses synchronous creation (runs at startup, no browser waiting)
        return createTenantFirstTime(null, req);
    }

    /**
     * Get tenant provisioning status for polling.
     * Returns current tenant status and basic info.
     *
     * @param tenantId tenant UUID
     * @return TenantResponse with current status
     */
    public TenantResponse getProvisioningStatus(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        // If ACTIVE, include full response with API key and subscription
        if ("ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            return buildResponse(tenant);
        }

        return buildResponseLightweight(tenant);
    }

    private static final int MAX_PROVISION_RETRIES = 1;

    /**
     * Retry provisioning for a stuck tenant.
     * Called by the scheduled retry job. Submits to the provisioning thread pool.
     * Skips if:
     * - Already ACTIVE
     * - Provisioning lock is held (another thread working on it)
     * - Max retries (5) exceeded → sets status to ABANDONED
     *
     * @param tenantId tenant UUID
     */
    public void retryProvisioning(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantNameWithUsers(
                tenantRepository.findByTenantID(tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId))
                        .getTenantName()
        ).orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if ("ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            return;
        }

        // Stop retrying after MAX_PROVISION_RETRIES — needs manual intervention
        if (tenant.getProvisionRetryCount() >= MAX_PROVISION_RETRIES) {
            if (!"ABANDONED".equalsIgnoreCase(tenant.getStatus())) {
                log.warn("[RETRY] Tenant '{}' exceeded max retries ({}). Setting status to ABANDONED. Last error: {}",
                        tenant.getTenantName(), MAX_PROVISION_RETRIES, tenant.getProvisionError());
                self.saveTenantStatus(tenant, "ABANDONED");
            }
            return;
        }

        String lockKey = tenant.getTenantName().toLowerCase();
        if (creationLocks.putIfAbsent(lockKey, Instant.now().toString()) != null) {
            log.debug("[RETRY] Provisioning already in progress for '{}', skipping.", tenant.getTenantName());
            return;
        }

        final String tenantName = tenant.getTenantName();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Tenant fresh = tenantRepository.findByTenantNameWithUsers(tenantName)
                        .orElseThrow(() -> new IllegalStateException("Tenant disappeared: " + tenantName));

                int attempt = fresh.getProvisionRetryCount() + 1;
                log.info("[RETRY] Attempt {}/{} for tenant '{}' (status={})",
                        attempt, MAX_PROVISION_RETRIES, tenantName, fresh.getStatus());

                // Increment retry count BEFORE attempting (so crash mid-retry still counts)
                fresh.setProvisionRetryCount(attempt);
                fresh.setProvisionError(null);
                self.persistTenantSkeleton(fresh, tenantName);

                CreateTenantRequest req = buildRetryRequest(fresh);
                resumeTenantSetup(fresh, req);

                // Success — reset retry count
                fresh.setProvisionRetryCount(0);
                fresh.setProvisionError(null);
                self.persistTenantSkeleton(fresh, tenantName);

                log.info("[RETRY] Provisioning completed for tenant '{}'", tenantName);
            } catch (Exception ex) {
                log.error("[RETRY] Attempt failed for tenant '{}': {}", tenantName, ex.getMessage(), ex);
                try {
                    Tenant t = tenantRepository.findByTenantID(tenantId).orElse(tenant);
                    t.setProvisionError(truncate(ex.getMessage(), 1000));
                    self.persistTenantSkeleton(t, tenantName);
                    self.saveTenantStatus(t, "FAILED");
                } catch (Exception statusEx) {
                    log.error("[RETRY] Could not update status for '{}': {}", tenantName, statusEx.getMessage());
                }
            } finally {
                creationLocks.remove(lockKey);
            }
        }, tenantProvisioningExecutor);
    }

    /**
     * Resend welcome email + required-action email for an ACTIVE tenant.
     * Looks up the admin user and fires the same emails that were sent during provisioning.
     *
     * @param tenantId tenant UUID
     */
    public void resendWelcomeEmail(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new GlobalException("Welcome email can only be resent for ACTIVE tenants. Current status: " + tenant.getStatus());
        }

        String loginUrl = tenant.getLoginUrl();
        String tenantDisplayName = tenant.getTenantName();
        String setPasswordUrl = buildSetPasswordUrl(tenant);

        // Query directly — avoids LazyInitializationException on tenant.getUsers()
        User adminUser = userRepository.findDefaultAdminByTenantId(tenantId)
                .orElseThrow(() -> new GlobalException("No admin user found for tenant: " + tenantDisplayName));

        String adminEmail = adminUser.getEmail();

        log.info("[RESEND-EMAIL] Resending welcome email for tenant '{}' to {}", tenantDisplayName, adminEmail);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            sendWithRetry(
                    () -> kcUtil.sendWelcomeEmail(adminEmail, loginUrl, tenantDisplayName, setPasswordUrl),
                    "Welcome email (resend)", adminEmail, 3, 2000
            );
            adminUser.setWelcomeEmailSentAt(Instant.now());
            adminUser.setWelcomeEmailSentCount(adminUser.getWelcomeEmailSentCount() + 1);
            userRepository.save(adminUser);
        });
    }

    /**
     * Resend only the reset-password (required-action) email for an ACTIVE tenant.
     * Use this when the admin received the welcome email but never set a password.
     *
     * @param tenantId tenant UUID
     */
    public void resendResetPasswordEmail(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new GlobalException("Reset password email can only be resent for ACTIVE tenants. Current status: " + tenant.getStatus());
        }

        if ("AZURE".equalsIgnoreCase(tenant.getSsoType())) {
            throw new GlobalException("Reset password is not applicable for SSO tenants. The admin authenticates via the enterprise identity provider.");
        }

        String realmName = tenant.getRealmName();

        // Query directly — avoids LazyInitializationException on tenant.getUsers()
        User adminUser = userRepository.findDefaultAdminByTenantId(tenantId)
                .orElseThrow(() -> new GlobalException("No admin user found for tenant: " + tenant.getTenantName()));

        String kcUserId = adminUser.getKeycloakUserId();
        if (kcUserId == null) {
            throw new GlobalException("Admin user has no Keycloak ID. Keycloak provisioning may be incomplete.");
        }

        log.info("[RESEND-RESET] Resending reset-password email for tenant '{}' to {}", tenant.getTenantName(), adminUser.getEmail());

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            sendWithRetry(
                    () -> kcUtil.sendRequiredActionEmail(realmName, kcUserId,
                            List.of("UPDATE_PASSWORD")),
                    "Reset password email (resend)", realmName + "/" + kcUserId, 3, 2000
            );
            adminUser.setResetEmailSentAt(Instant.now());
            adminUser.setResetEmailSentCount(adminUser.getResetEmailSentCount() + 1);
            userRepository.save(adminUser);
        });
    }

    /**
     * Set a temporary password for the default admin user of a sub-tenant.
     * <p>
     * Intended for MSSP admins whose Enterprise sub-tenant has no email provider
     * (e.g. no Microsoft 365 subscription) and therefore cannot receive the
     * Keycloak welcome/required-action email. The MSSP sets a known temporary
     * password and delivers it to the Enterprise admin out-of-band. Keycloak
     * marks it as temporary so the admin is forced to change it on first login.
     * <p>
     * Authorization: the caller's tenant must be the direct parent of {@code targetTenantId}.
     *
     * @param callerTenantId the tenantId from the caller's JWT (must be the parent)
     * @param targetTenantId the sub-tenant whose admin password should be set
     * @param temporaryPassword the temporary password to set (minimum 8 characters)
     */
    public void setAdminTemporaryPassword(String callerTenantId, String targetTenantId, String temporaryPassword) {
        log.info("setAdminTemporaryPassword: callerTenantId={} targetTenantId={}", callerTenantId, targetTenantId);

        Tenant tenant = tenantRepository.findById(targetTenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + targetTenantId));

        // Caller must be the direct parent of the target tenant
        if (!callerTenantId.equals(tenant.getParentTenantId())) {
            throw new GlobalException("Access denied: you do not manage tenant " + targetTenantId);
        }

        // Find the default admin user
        User adminUser = tenant.getUsers().stream()
                .filter(User::isDefaultUser)
                .findFirst()
                .orElseThrow(() -> new GlobalException(
                        "No admin user found for tenant: " + tenant.getTenantName()));

        String kcUserId = adminUser.getKeycloakUserId();
        if (kcUserId == null || kcUserId.isBlank()) {
            throw new GlobalException(
                    "Admin user has no Keycloak ID — provisioning may not be complete for tenant: "
                            + tenant.getTenantName());
        }

        // Set temporary password in Keycloak (temporary=true forces change on next login)
        kcUtil.setUserPassword(tenant.getRealmName(), kcUserId, temporaryPassword, true);
        log.info("setAdminTemporaryPassword: temporary password set for admin of tenant '{}' (tenantId={})",
                tenant.getTenantName(), targetTenantId);
    }

    /**
     * Manual retry triggered by admin via API.
     * Resets the retry count so the tenant gets another chance,
     * then delegates to the normal retry flow.
     *
     * @param tenantId tenant UUID
     */
    @Transactional
    public void manualRetryProvisioning(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        String status = tenant.getStatus();
        if ("ACTIVE".equalsIgnoreCase(status)) {
            throw new GlobalException("Tenant is already ACTIVE — no retry needed.");
        }
        if ("CREATING".equalsIgnoreCase(status) || "CREATED_LOCAL".equalsIgnoreCase(status)
                || "REALM_CREATED".equalsIgnoreCase(status) || "CLIENT_CREATED".equalsIgnoreCase(status)
                || "USER_CREATED".equalsIgnoreCase(status)) {
            // Check if actively being provisioned
            String lockKey = tenant.getTenantName().toLowerCase();
            if (creationLocks.containsKey(lockKey)) {
                throw new GlobalException("Provisioning is already in progress for this tenant.");
            }
        }

        log.info("[MANUAL-RETRY] Admin triggered retry for tenant '{}' (status={}, previousRetries={})",
                tenant.getTenantName(), status, tenant.getProvisionRetryCount());

        // Reset retry count so the scheduler/retry logic allows another attempt
        tenant.setProvisionRetryCount(0);
        tenant.setProvisionError(null);
        tenant.setStatus("FAILED"); // Ensure it's in a retriable state
        tenantRepository.save(tenant);

        // Trigger retry immediately
        retryProvisioning(tenantId);
    }

    private static String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) : s;
    }


    @Transactional
    public int repairActiveTenants() {
        List<Tenant> activeTenants = tenantRepository.findByStatus("ACTIVE");
        int repaired = 0;

        for (Tenant tenant : activeTenants) {
            if (repairTenantIfNeeded(tenant)) {
                repaired++;
            }
        }

        log.info("[REPAIR] Done. {} tenant(s) repaired out of {} active.", repaired, activeTenants.size());
        return repaired;
    }

    /**
     * Normalize all Keycloak realm names to lowercase.
     * <p>
     * For each tenant where realmName != lowercase(realmName):
     * 1. Rename the Keycloak realm via Admin API
     * 2. Update tenant.realmName and tenant.tenantName in DB
     * 3. Update auth_provider_config URLs (issuer, token, JWK, clientId, loginUrl)
     * <p>
     * Idempotent — skips tenants that are already lowercase.
     * WARNING: Active sessions in renamed realms will be invalidated.
     */
    public String normalizeRealmNamesToLowercase() {
        List<Tenant> allTenants = tenantRepository.findAll();
        int renamed = 0;
        int skipped = 0;
        int failed = 0;
        StringBuilder report = new StringBuilder();

        for (Tenant tenant : allTenants) {
            String currentRealm = tenant.getRealmName();
            if (currentRealm == null) {
                skipped++;
                continue;
            }

            String targetRealm = currentRealm.toLowerCase();
            if (currentRealm.equals(targetRealm)) {
                skipped++;
                continue;
            }

            log.info("[NORMALIZE] Processing tenant '{}': realm '{}' → '{}'",
                    tenant.getTenantName(), currentRealm, targetRealm);

            try {
                // Step 1: Rename realm in Keycloak
                boolean kcRenamed = kcUtil.renameRealm(currentRealm, targetRealm);

                if (!kcRenamed && !kcUtil.realmExists(targetRealm)) {
                    log.warn("[NORMALIZE] Realm '{}' not found in Keycloak and target '{}' doesn't exist either. Skipping.",
                            currentRealm, targetRealm);
                    report.append("SKIP: ").append(currentRealm).append(" (not found in KC)\n");
                    skipped++;
                    continue;
                }

                // Step 2: Update DB — tenant name and realm name
                tenant.setRealmName(targetRealm);
                tenant.setTenantName(tenant.getTenantName().toLowerCase());

                // Step 3: Regenerate login URL with lowercase realm
                String loginUrl = tenant.getLoginUrl();
                if (loginUrl != null && loginUrl.contains("/realms/" + currentRealm)) {
                    loginUrl = loginUrl.replace("/realms/" + currentRealm, "/realms/" + targetRealm);
                    loginUrl = loginUrl.replace("client_id=" + currentRealm, "client_id=" + targetRealm);
                    // Also handle original tenantName in client_id if it differs from realm
                    String currentTenantName = tenant.getTenantName();
                    if (!currentRealm.equals(currentTenantName)) {
                        loginUrl = loginUrl.replace("client_id=" + currentTenantName, "client_id=" + targetRealm);
                    }
                    tenant.setLoginUrl(loginUrl);
                }

                tenantRepository.save(tenant);

                // Step 4: Update auth_provider_config
                authProviderConfigRepository.findByTenant(tenant).ifPresent(cfg -> {
                    if (cfg.getIssuerUri() != null) {
                        cfg.setIssuerUri(cfg.getIssuerUri().replace("/realms/" + currentRealm, "/realms/" + targetRealm));
                    }
                    if (cfg.getTokenEndpoint() != null) {
                        cfg.setTokenEndpoint(cfg.getTokenEndpoint().replace("/realms/" + currentRealm, "/realms/" + targetRealm));
                    }
                    if (cfg.getJwkUri() != null) {
                        cfg.setJwkUri(cfg.getJwkUri().replace("/realms/" + currentRealm, "/realms/" + targetRealm));
                    }
                    cfg.setClientId(targetRealm);
                    if (cfg.getLoginUrl() != null) {
                        String cfgLoginUrl = cfg.getLoginUrl();
                        cfgLoginUrl = cfgLoginUrl.replace("/realms/" + currentRealm, "/realms/" + targetRealm);
                        cfgLoginUrl = cfgLoginUrl.replace("client_id=" + currentRealm, "client_id=" + targetRealm);
                        cfg.setLoginUrl(cfgLoginUrl);
                    }
                    authProviderConfigRepository.save(cfg);
                    log.info("[NORMALIZE] Updated auth_provider_config for tenant '{}'", tenant.getTenantName());
                });

                renamed++;
                report.append("OK: ").append(currentRealm).append(" → ").append(targetRealm).append("\n");
                log.info("[NORMALIZE] Successfully renamed realm '{}' → '{}'", currentRealm, targetRealm);

            } catch (Exception e) {
                failed++;
                report.append("FAIL: ").append(currentRealm).append(" — ").append(e.getMessage()).append("\n");
                log.error("[NORMALIZE] Failed to rename realm '{}': {}", currentRealm, e.getMessage(), e);
            }
        }

        String summary = String.format("Renamed: %d, Skipped: %d, Failed: %d\n%s",
                renamed, skipped, failed, report);
        log.info("[NORMALIZE] Done. {}", summary);
        return summary;
    }

    /**
     * Repair a single tenant by ID. Creates only the artifacts that are missing.
     *
     * @param tenantId the tenant ID to repair
     * @return true if any artifact was missing and created
     */
    @Transactional
    public boolean repairTenantById(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new GlobalException("Tenant is not ACTIVE (status=" + tenant.getStatus()
                    + "). Use retry-provisioning for non-ACTIVE tenants.");
        }

        return repairTenantIfNeeded(tenant);
    }


    private boolean repairTenantIfNeeded(Tenant tenant) {
        boolean wasRepaired = false;
        String tenantId = tenant.getTenantID();
            String tenantName = tenant.getTenantName();
            CreateTenantRequest req = buildRetryRequest(tenant);
            String ssoType = req.getSsoType();

            // ── 1. Tenant code — only if missing ──
            if (tenant.getTenantCode() == null || tenant.getTenantCode().isBlank()) {
                log.warn("[REPAIR] Tenant '{}' (id={}) missing tenantCode. Creating.", tenantName, tenantId);
                tenant.setTenantCode(generateUniqueTenantCode(tenantName));
                tenantRepository.save(tenant);
                wasRepaired = true;
            }

            // ── 2. Login URL — only if missing ──
            if (tenant.getLoginUrl() == null || tenant.getLoginUrl().isBlank()) {
                log.warn("[REPAIR] Tenant '{}' (id={}) missing loginUrl. Creating.", tenantName, tenantId);
                tenant.setLoginUrl(generateLoginUrl(req));
                tenantRepository.save(tenant);
                wasRepaired = true;
            }

            // ── 3. Auth provider config — only if missing ──
            if (authProviderConfigRepository.findByTenant(tenant).isEmpty()) {
                log.warn("[REPAIR] Tenant '{}' (id={}) missing auth_provider_config. Creating.", tenantName, tenantId);
                self.saveAuthProviderConfig(tenant, req);
                wasRepaired = true;
            }

            // ── 4. Admin roles, group, and user-group mapping ──
            try {
                // Find admin user
                User admin = tenant.getUsers() != null
                        ? tenant.getUsers().stream().filter(User::isDefaultUser).findFirst().orElse(null)
                        : null;
                if (admin == null && tenant.getUsers() == null) {
                    // Re-fetch with users if not loaded
                    Tenant withUsers = tenantRepository.findByTenantIDWithUsers(tenantId).orElse(null);
                    if (withUsers != null) {
                        admin = withUsers.getUsers().stream().filter(User::isDefaultUser).findFirst().orElse(null);
                    }
                }

                if (admin != null) {
                    // Ensure roles exist
                    List<Roles> adminRoles = roleService.resolveAdminRolesForTenant(tenant, admin.getPkUserId());
                    List<Roles> validRoles = adminRoles.stream().filter(java.util.Objects::nonNull).toList();

                    if (!validRoles.isEmpty()) {
                        // Ensure admin group exists
                        String adminGroupName = tenantName + "_Admin";
                        Groups adminGroup = groupService.createOrGetDefaultGroup(
                                tenantId, adminGroupName, true, admin.getPkUserId());

                        // Assign roles to group
                        validRoles.forEach(role -> groupService.assignRoleToGroupIfMissing(adminGroup, role));

                        // Link user to group if not linked — direct SQL to avoid Hibernate flush issues
                        if (userRepository.countUserGroupMapping(admin.getPkUserId(), adminGroup.getPkGroupId()) == 0) {
                            log.warn("[REPAIR] Tenant '{}' admin user not linked to group. Linking.", tenantName);
                            userRepository.insertUserGroupMappingIfAbsent(admin.getPkUserId(), adminGroup.getPkGroupId());
                            wasRepaired = true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[REPAIR] Failed to repair admin roles/group for tenant '{}': {}", tenantName, e.getMessage());
            }

            // ── 5. Default policies — each service internally checks "already exists?" and skips ──
            try {
                browserPolicyService.createTenantDefaultPolicy(tenantId);
            } catch (Exception e) {
                log.warn("[REPAIR] Failed to create BrowserPolicy for tenant '{}': {}", tenantName, e.getMessage());
            }
            try {
                networkPolicyService.createTenantDefaultPolicy(tenantId);
            } catch (Exception e) {
                log.warn("[REPAIR] Failed to create NetworkPolicy for tenant '{}': {}", tenantName, e.getMessage());
            }
            try {
                extensionPolicyService.createTenantDefaultPolicy(tenantId);
            } catch (Exception e) {
                log.warn("[REPAIR] Failed to create ExtensionPolicy for tenant '{}': {}", tenantName, e.getMessage());
            }

            // ─��� 5. APIKEY tenants: default EventsGroup + PolicyAssignment — only if missing ──
            if ("APIKEY".equalsIgnoreCase(ssoType)) {
                try {
                    // createDefaultGroupForTenant internally returns existing if present
                    EventsGroup defaultGroup = eventsGroupService.createDefaultGroupForTenant(tenantId, "SYSTEM");

                    if (policyAssignmentRepository.countByTenantId(tenantId) == 0) {
                        log.warn("[REPAIR] Tenant '{}' missing policy assignments. Creating.", tenantName);
                        BrowserPolicy bp = browserPolicyService.createTenantDefaultPolicy(tenantId);
                        NetworkPolicy np = networkPolicyService.createTenantDefaultPolicy(tenantId);
                        ExtensionPolicy ep = extensionPolicyService.createTenantDefaultPolicy(tenantId);

                        List<PolicyAssignment> assignments = List.of(
                                PolicyAssignment.builder()
                                        .browserPolicy(bp).networkPolicy(null).extensionPolicy(null)
                                        .azureResourceId(defaultGroup.getPkEventsGroupId())
                                        .azureResourceName(defaultGroup.getName())
                                        .assignmentType("GROUP").tenantId(tenantId)
                                        .assignedAt(LocalDateTime.now()).build(),
                                PolicyAssignment.builder()
                                        .browserPolicy(null).networkPolicy(np).extensionPolicy(null)
                                        .azureResourceId(defaultGroup.getPkEventsGroupId())
                                        .azureResourceName(defaultGroup.getName())
                                        .assignmentType("GROUP").tenantId(tenantId)
                                        .assignedAt(LocalDateTime.now()).build(),
                                PolicyAssignment.builder()
                                        .browserPolicy(null).networkPolicy(null).extensionPolicy(ep)
                                        .azureResourceId(defaultGroup.getPkEventsGroupId())
                                        .azureResourceName(defaultGroup.getName())
                                        .assignmentType("GROUP").tenantId(tenantId)
                                        .assignedAt(LocalDateTime.now()).build()
                        );
                        policyAssignmentRepository.saveAll(assignments);
                        wasRepaired = true;
                    }
                } catch (Exception e) {
                    log.warn("[REPAIR] Failed to create APIKEY group/assignments for tenant '{}': {}", tenantName, e.getMessage());
                }
            }

            // ── 6. selfManaged non-APIKEY tenants: default EventsGroup — returns existing if present ──
            if (Boolean.TRUE.equals(tenant.getSelfManaged()) && !"APIKEY".equalsIgnoreCase(ssoType)) {
                try {
                    eventsGroupService.createDefaultGroupForTenant(tenantId, "SYSTEM");
                } catch (Exception e) {
                    log.warn("[REPAIR] Failed to create default EventsGroup for tenant '{}': {}", tenantName, e.getMessage());
                }
            }

            // ── 7. Default subscription — only if no active subscription exists ──
            try {
                if (!subscriptionService.hasActiveSubscription(tenantId)) {
                    log.warn("[REPAIR] Tenant '{}' (id={}) has no active subscription. Creating default.", tenantName, tenantId);
                    subscriptionService.createDefaultSubscription(tenantId, tenant.getCreatedBy());
                    wasRepaired = true;
                }
            } catch (Exception e) {
                log.warn("[REPAIR] Failed to create subscription for tenant '{}': {}", tenantName, e.getMessage());
            }

        if (wasRepaired) {
            log.info("[REPAIR] Tenant '{}' (id={}) repaired.", tenantName, tenantId);
        } else {
            log.info("[REPAIR] Tenant '{}' (id={}) — nothing missing.", tenantName, tenantId);
        }

        return wasRepaired;
    }


    private CreateTenantRequest buildRetryRequest(Tenant tenant) {
        CreateTenantRequest req = new CreateTenantRequest();
        req.setTenantName(tenant.getTenantName());
        req.setDomain(tenant.getDomain());
        req.setEmail(tenant.getEmail());
        req.setRegion(tenant.getRegion());
        req.setPhoneNo(tenant.getPhoneNo());
        req.setTenantType(tenant.getTenantType());
        req.setIndustry(tenant.getIndustry());
        req.setTemporaryAddress(tenant.getTemporaryAddress());
        req.setPermanentAddress(tenant.getPermanentAddress());
        req.setBillingAddress(tenant.getBillingAddress());
        req.setBillingCycleType(tenant.getBillingCycleType());

        // SSO type is now persisted on the Tenant entity since creation.
        // Fall back to auth_provider_config or artifact inference for legacy tenants.
        String resolvedSsoType = tenant.getSsoType();
        if (resolvedSsoType == null || resolvedSsoType.isBlank()) {
            resolvedSsoType = authProviderConfigRepository.findByTenant(tenant)
                    .map(AuthProviderConfig::getSsoType)
                    .orElse(null);
        }
        if (resolvedSsoType == null) {
            if (!apiKeyRepository.findByTenantId(tenant.getTenantID()).isEmpty()) {
                resolvedSsoType = "APIKEY";
            } else {
                try {
                    if (kcUtil.idpExists(tenant.getRealmName(), "master-hub")) {
                        resolvedSsoType = "AZURE";
                    }
                } catch (Exception e) {
                    log.warn("buildRetryRequest: could not check IdP for realm '{}': {}",
                            tenant.getRealmName(), e.getMessage());
                }
            }
        }
        req.setSsoType(resolvedSsoType);

        // Get admin details from default user
        if (tenant.getUsers() != null) {
            tenant.getUsers().stream()
                    .filter(u -> u.isDefaultUser())
                    .findFirst()
                    .ifPresent(admin -> {
                        req.setAdminEmail(admin.getEmail());
                        req.setAdminFirstName(admin.getFirstName());
                        req.setAdminLastName(admin.getLastName());
                        req.setAdminUserName(admin.getUserName());
                        req.setAdminPhoneNumber(admin.getPhoneNo());
                    });
        }

        return req;
    }


    public TenantResponse createTenant(HttpServletRequest request, CreateTenantRequest req) {

        String lockKey = req.getTenantName().toLowerCase();

        // Fast path: if tenant already ACTIVE, return immediately without acquiring lock
        Optional<Tenant> existingOpt =
                tenantRepository.findByTenantNameWithUsers(req.getTenantName().toLowerCase());

        if (existingOpt.isPresent()) {
            Tenant existing = existingOpt.get();
            if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                throw new KeycloakOperationException(
                        "TENANT_ALREADY_ACTIVE", 1015, "Tenant already active"
                );
            }
            // Provisioning already in progress — return current status for polling
            log.info("createTenant: tenant '{}' already exists with status '{}'. Returning for polling.",
                    req.getTenantName(), existing.getStatus());
            return buildResponseLightweight(existing);
        }

        // Acquire per-tenant-name lock to prevent concurrent first-time creation.
        if (creationLocks.putIfAbsent(lockKey, Instant.now().toString()) != null) {
            throw new KeycloakOperationException(
                    "TENANT_CREATION_IN_PROGRESS", 1016,
                    "Tenant creation is already in progress for: " + req.getTenantName()
            );
        }

        try {
            // Re-check after acquiring lock — another thread may have just finished
            existingOpt = tenantRepository.findByTenantNameWithUsers(req.getTenantName().toLowerCase());

            if (existingOpt.isPresent()) {
                Tenant existing = existingOpt.get();
                if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                    throw new KeycloakOperationException(
                            "TENANT_ALREADY_ACTIVE", 1015, "Tenant already active"
                    );
                }
                return buildResponseLightweight(existing);
            }

            if (strictValidation) {
                String tenantDomain = extractDomain(req.getEmail());
                String userDomain = extractDomain(req.getAdminEmail());

                if (tenantRepository.existsTenantByEmailDomain(tenantDomain)) {
                    throw new DomainAlreadyExistsException(
                            "Email domain already in use by another tenant: " + tenantDomain
                    );
                }

                if (tenantDomain == null || !tenantDomain.equals(userDomain)) {
                    throw new AccessDeniedException(
                            "Admin email domain and user email domain must match"
                    );
                }
            }
            // First time ONLY
            validateForFirstTime(req, request == null);

            // Persist skeleton synchronously (fast — DB only)
            return createTenantSkeletonAndProvisionAsync(request, req);
        } finally {
            creationLocks.remove(lockKey);
        }
    }


    private TenantResponse createTenantSkeletonAndProvisionAsync(
            HttpServletRequest request,
            CreateTenantRequest req
    ) {
        LoggedInUserDetailsBean loggedInUserDetailsBean = null;

        if (request != null) {
            loggedInUserDetailsBean =
                    (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        }
        Tenant tenant = buildTenantSkeleton(req);
        tenant.setStatus("CREATING");

        // Set parent linkage BEFORE persisting so it's included in the initial save
        if (loggedInUserDetailsBean != null) {
            tenant.setParentTenantId(loggedInUserDetailsBean.getTenantId());
            tenant.setCreatedBy(loggedInUserDetailsBean.getUsername());
        }
        self.persistTenantSkeleton(tenant, req.getTenantName());

        User admin = buildAdminSkeleton(req, tenant);
        admin.setStatus("CREATING");
        self.persistAdminSkeleton(admin, req.getTenantName());

        List<User> users = new ArrayList<>();
        users.add(admin);
        tenant.setUsers(users);

        self.saveTenantStatus(tenant, "CREATED_LOCAL");

        // Fire-and-forget: run Keycloak provisioning in background.
        // The frontend will poll GET /api/tenants/{id}/provisioning-status.
        final String tenantId = tenant.getTenantID();
        final String tenantName = tenant.getTenantName();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String lockKey2 = tenantName.toLowerCase();
            // Acquire provisioning lock — prevents duplicate background runs
            if (creationLocks.putIfAbsent(lockKey2, Instant.now().toString()) != null) {
                log.warn("[ASYNC-PROVISION] Lock already held for tenant '{}', skipping duplicate run.", tenantName);
                return;
            }
            try {
                log.info("[ASYNC-PROVISION] Starting background provisioning for tenant '{}'", tenantName);
                // Re-fetch tenant with users for provisioning (fresh persistence context)
                Tenant t = tenantRepository.findByTenantNameWithUsers(tenantName)
                        .orElseThrow(() -> new IllegalStateException("Tenant disappeared: " + tenantName));
                resumeTenantSetup(t, req);
                log.info("[ASYNC-PROVISION] Provisioning completed for tenant '{}'", tenantName);
            } catch (Exception ex) {
                log.error("[ASYNC-PROVISION] Provisioning failed for tenant '{}'. Setting status to FAILED.", tenantName, ex);
                try {
                    self.saveTenantStatus(
                            tenantRepository.findById(tenantId).orElse(tenant),
                            "FAILED"
                    );
                } catch (Exception statusEx) {
                    log.error("[ASYNC-PROVISION] Could not set FAILED status for tenant '{}': {}", tenantName, statusEx.getMessage());
                }
            } finally {
                creationLocks.remove(lockKey2);
            }
        }, tenantProvisioningExecutor);

        // Return immediately — tenant is persisted in DB with status CREATED_LOCAL
        return buildResponseLightweight(tenant);
    }

    /**
     * Synchronous tenant creation (used by system-level creation where async is not needed).
     * Persists skeleton and runs full provisioning in the same thread.
     */
    private TenantResponse createTenantFirstTime(
            HttpServletRequest request,
            CreateTenantRequest req
    ) {
        LoggedInUserDetailsBean loggedInUserDetailsBean = null;

        if (request != null) {
            loggedInUserDetailsBean =
                    (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        }
        Tenant tenant = buildTenantSkeleton(req);
        tenant.setStatus("CREATING");

        if (loggedInUserDetailsBean != null) {
            tenant.setParentTenantId(loggedInUserDetailsBean.getTenantId());
            tenant.setCreatedBy(loggedInUserDetailsBean.getUsername());
        }
        self.persistTenantSkeleton(tenant, req.getTenantName());

        try {
            User admin = buildAdminSkeleton(req, tenant);
            admin.setStatus("CREATING");
            self.persistAdminSkeleton(admin, req.getTenantName());

            List<User> users = new ArrayList<>();
            users.add(admin);
            tenant.setUsers(users);

            self.saveTenantStatus(tenant, "CREATED_LOCAL");

            return resumeTenantSetup(tenant, req);
        } catch (Exception ex) {
            log.error("[PROVISION] First-time tenant creation failed for tenantName={}. Initiating rollback.",
                    tenant.getTenantName(), ex);
            try {
                self.rollbackFailedCreation(tenant);
            } catch (Exception rollbackEx) {
                log.error("[PROVISION] Rollback itself failed for tenantName={}. Manual cleanup required.",
                        tenant.getTenantName(), rollbackEx);
            }
            throw ex;
        }
    }


    TenantResponse resumeTenantSetup(
            Tenant tenant,
            CreateTenantRequest req
    ) {
        // 🔄 Recovery: if tenant is in early state and has no admin user
        // (e.g. previous creation failed after tenant persist but before admin persist),
        // re-create the admin skeleton so provisioning can continue.
        // This MUST run before reconcileAdminMutableFields which requires an admin to exist.
        if (("CREATING".equalsIgnoreCase(tenant.getStatus())
                || "CREATED_LOCAL".equalsIgnoreCase(tenant.getStatus()))
                && (tenant.getUsers() == null || tenant.getUsers().isEmpty())) {

            log.warn("[PROVISION] Tenant '{}' in state {} has no admin user. Re-creating admin skeleton.",
                    tenant.getTenantName(), tenant.getStatus());
            User admin = buildAdminSkeleton(req, tenant);
            admin.setStatus("CREATING");
            self.persistAdminSkeleton(admin, req.getTenantName());
            List<User> users = new ArrayList<>();
            users.add(admin);
            tenant.setUsers(users);
            self.saveTenantStatus(tenant, "CREATED_LOCAL");
        }

        // 🔁 Reconcile mutable fields (runs after admin recovery to avoid NPE)
        reconcileTenantMutableFields(tenant, req);
        reconcileAdminMutableFields(tenant, req);

        return switch (tenant.getStatus()) {
            case "CREATED_LOCAL", "CREATING", "FAILED" -> {
                long _t0 = System.currentTimeMillis();
                ensureRealm(tenant, req);
                log.info("[PROVISION] ensureRealm done in {}ms for tenant={}", System.currentTimeMillis() - _t0, tenant.getTenantName());
                long _t1 = System.currentTimeMillis();
                ensureClient(tenant, req);
                log.info("[PROVISION] ensureClient done in {}ms for tenant={}", System.currentTimeMillis() - _t1, tenant.getTenantName());
                long _t2 = System.currentTimeMillis();
                ensureAdminUser(tenant, req);
                log.info("[PROVISION] ensureAdminUser done in {}ms for tenant={}", System.currentTimeMillis() - _t2, tenant.getTenantName());
                self.saveTenantStatus(tenant, "USER_CREATED");
                long _t3 = System.currentTimeMillis();
                finalizeTenant(tenant, req);
                log.info("[PROVISION] finalizeTenant done in {}ms for tenant={}", System.currentTimeMillis() - _t3, tenant.getTenantName());
                yield buildResponse(tenant);
            }
            case "REALM_CREATED" -> {
                ensureRealm(tenant, req);   // verify realm actually exists in Keycloak
                ensureClient(tenant, req);
                ensureAdminUser(tenant, req);
                self.saveTenantStatus(tenant, "USER_CREATED");
                finalizeTenant(tenant, req);
                yield buildResponse(tenant);
            }
            case "CLIENT_CREATED" -> {
                ensureAdminUser(tenant, req);
                self.saveTenantStatus(tenant, "USER_CREATED");
                finalizeTenant(tenant, req);
                yield buildResponse(tenant);
            }
            case "USER_CREATED" -> {
                finalizeTenant(tenant, req);
                yield buildResponse(tenant);
            }
            case "ACTIVE" -> buildResponse(tenant);
            default -> throw new KeycloakOperationException(
                    "INVALID_STATE", 1013, "Invalid tenant state"
            );
        };
    }
    /**
     * Ensures Master IdP exists, creates Organization, and links them.
     * Then creates a Broker Client in Master and a Spoke IdP in the Tenant realm.
     */
    private void setupHubAndSpokeSSO(Tenant tenant, CreateTenantRequest req) {
        String masterRealm = "master";
        String tenantRealm = tenant.getRealmName();
        String orgAlias = tenant.getTenantName().toLowerCase().replaceAll("\\s+", "-");

        long t0 = System.currentTimeMillis();
        log.info("[SSO] Starting Hub & Spoke setup for tenant: {}", tenant.getTenantName());

        try {
            // --- STEP 1: Ensure Master Azure IdP exists ---
            long t1 = System.currentTimeMillis();
            if (!kcUtil.idpExists(masterRealm, masterAzureAlias)) {
                log.info("[SSO] Step1: Master IdP '{}' not found. Creating.", masterAzureAlias);
                CreateIdentityProviderRequest masterIdpReq = new CreateIdentityProviderRequest();
                masterIdpReq.setAlias(masterAzureAlias);
                masterIdpReq.setDisplayName("Enterprise Azure Login");
                masterIdpReq.setClientId(masterAzureClientId);
                masterIdpReq.setClientSecret(masterAzureClientSecret);
                masterIdpReq.setEnabled(true);
                kcUtil.addIdentityProvider(masterRealm, masterIdpReq);
                log.info("[SSO] Step1: Master IdP created. ({}ms)", System.currentTimeMillis() - t1);
            } else {
                log.info("[SSO] Step1: Master IdP already exists. ({}ms)", System.currentTimeMillis() - t1);
            }

            // --- STEP 2: Create Organization & Link IdP in Master (non-fatal) ---
            long t2 = System.currentTimeMillis();
            try {
                kcUtil.createOrganization(masterRealm, tenant.getTenantName(), tenant.getDomain());
                kcUtil.linkIdpToOrganization(masterRealm, orgAlias, masterAzureAlias);
                log.info("[SSO] Step2: Org setup done. ({}ms)", System.currentTimeMillis() - t2);
            } catch (Exception e) {
                log.warn("[SSO] Step2: Organization setup skipped for '{}': {}", tenant.getTenantName(), e.getMessage());
            }

            // --- STEP 3: Create the Broker Client in Master (The Hub side) — idempotent ---
            long t3 = System.currentTimeMillis();
            String brokerClientId = "broker-for-" + tenantRealm;
            ClientRepresentation brokerClient;
            if (kcUtil.clientExists(masterRealm, brokerClientId)) {
                log.info("[SSO] Step3: Broker client already exists, reusing. ({}ms)", System.currentTimeMillis() - t3);
                brokerClient = kcUtil.getClientWithSecret(masterRealm, brokerClientId);
            } else {
                brokerClient = createBrokerClientInMaster(tenantRealm, tenant.getDomain());
                log.info("[SSO] Step3: Broker client created. ({}ms)", System.currentTimeMillis() - t3);
            }
            // Always ensure broker client has the attribute mappers (idempotent)
            kcUtil.configureBrokerClientMappers(tenantRealm);

            // --- STEP 4: Create or update the IdP in Tenant Realm (The Spoke side) — idempotent ---
            long t4 = System.currentTimeMillis();
            if (!kcUtil.idpExists(tenantRealm, "master-hub")) {
                createMasterHubIdpInTenant(tenantRealm, brokerClient.getClientId(), brokerClient.getSecret());
                kcUtil.configureMasterHubIdpMappers(tenantRealm);
                log.info("[SSO] Step4: master-hub IdP created with mappers. ({}ms)", System.currentTimeMillis() - t4);
            } else {
                // Always update the authorizationUrl, client credentials, and mappers
                kcUtil.updateMasterHubIdpWithCredentials(tenantRealm, brokerClient.getClientId(), brokerClient.getSecret());
                kcUtil.configureMasterHubIdpMappers(tenantRealm);
                log.info("[SSO] Step4: master-hub IdP updated with credentials, authorizationUrl and mappers. ({}ms)", System.currentTimeMillis() - t4);
            }

            // --- STEP 5: Force Auto-Redirect (skip Keycloak screen for Azure tenants) ---
            long t5 = System.currentTimeMillis();
            kcUtil.setAutoRedirect(tenantRealm, "master-hub");
            log.info("[SSO] Step5: Auto-redirect set. ({}ms)", System.currentTimeMillis() - t5);

            // --- STEP 6: Add client protocol mappers to tenant realm client ---
            // Writes azure_tenant_id, azure_roles, azure_groups into the app JWT
            long t6 = System.currentTimeMillis();
            kcUtil.configureTenantClientMappers(tenantRealm, tenant.getTenantName());
            log.info("[SSO] Step6: Tenant client mappers configured. ({}ms)", System.currentTimeMillis() - t6);

            // --- STEP 7: Align master realm session lifetimes for brokered SSO ---
            // Ensures master realm sessions don't expire before tenant realm refresh tokens
            long t7 = System.currentTimeMillis();
            try {
                kcUtil.updateRealmTokenSettings(masterRealm, 3600, 7200, 28800);
                log.info("[SSO] Step7: Master realm token settings aligned. ({}ms)", System.currentTimeMillis() - t7);
            } catch (Exception e2) {
                log.warn("[SSO] Step7: Master realm token settings update failed (non-fatal): {}", e2.getMessage());
            }

            log.info("[SSO] Hub & Spoke setup complete for tenant: {} (total {}ms)", orgAlias, System.currentTimeMillis() - t0);

        } catch (Exception e) {
            log.error("Failed to provision Hub & Spoke SSO: {}", e.getMessage());
            throw new GlobalException("SSO_PROVISIONING_FAILED", e.getMessage());
        }
    }


    public ClientRepresentation createBrokerClientInMaster(String tenantRealmName, String domain) {
        String clientId = "broker-for-" + tenantRealmName;
        String secret = UUID.randomUUID().toString(); // Generate a unique secret for this tenant

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setSecret(secret);
        client.setServiceAccountsEnabled(true);
        client.setAuthorizationServicesEnabled(true);
        String masterCallback = baseUrl + "/realms/" + tenantRealmName + "/broker/master-hub/endpoint";
        client.setRedirectUris(List.of(masterCallback));
        kcUtil.createClient("master", client);
        return client;
    }

    public void createMasterHubIdpInTenant(String tenantRealm, String masterClientId, String masterClientSecret) {
        IdentityProviderRepresentation idp = new IdentityProviderRepresentation();
        idp.setAlias("master-hub");
        idp.setProviderId("keycloak-oidc");
        idp.setEnabled(true);

        idp.setTrustEmail(true);

        Map<String, String> config = new HashMap<>();
        // Append kc_idp_hint=microsoft directly to the authorization URL so that when the
        // tenant realm forwards the broker request to master, master immediately redirects
        // to Azure AD without showing its own login screen.
        config.put("authorizationUrl", baseUrl + "/realms/master/protocol/openid-connect/auth?kc_idp_hint=microsoft");
        config.put("tokenUrl", baseUrl + "/realms/master/protocol/openid-connect/token");
        config.put("clientId", masterClientId);
        config.put("clientSecret", masterClientSecret);
        idp.setConfig(config);
        kcUtil.createIdp(tenantRealm, idp);
    }


    private String escapeLikePattern(String input) {
        if (input == null) return null;
        return input.replace("%", "").replace("_", "");
    }

    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.lastIndexOf("@") + 1).toLowerCase();
    }


    private void ensureRealm(Tenant tenant, CreateTenantRequest req) {

        if (kcUtil.realmExists(tenant.getRealmName())) {
            log.info("Realm '{}' already exists in Keycloak. Binding to tenant.", tenant.getRealmName());
            self.saveTenantStatus(tenant, "REALM_CREATED");
            self.saveProvisionSteps(tenant,
                    ProvisionStep.markCompleted(tenant.getProvisionStepsCompleted(), ProvisionStep.REALM_CREATED));
            return;
        }

        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(tenant.getRealmName());
        realm.setEnabled(true);
        realm.setSmtpServer(getSmtpConfig());

        try {
            kcUtil.createRealm(realm);
        } catch (Exception e) {
            // Check if realm was created by another process (race condition or default tenant resume)
            try {
                if (kcUtil.realmExists(tenant.getRealmName())) {
                    log.info("Realm '{}' created by another process or already exists. Binding to tenant.", tenant.getRealmName());
                    self.saveTenantStatus(tenant, "REALM_CREATED");
                    self.saveProvisionSteps(tenant,
                            ProvisionStep.markCompleted(tenant.getProvisionStepsCompleted(), ProvisionStep.REALM_CREATED));
                    return;
                }
            } catch (Exception checkEx) {
                log.error("ensureRealm: Keycloak unreachable during fallback check for realm '{}': {}",
                        tenant.getRealmName(), checkEx.getMessage());
            }
            // Extract root cause for a clear error message
            String rootCause = e.getMessage();
            if (e.getCause() != null) {
                rootCause = e.getCause().getMessage();
            }
            throw new GlobalException("REALM_CREATION_FAILED",
                    "Realm creation failed for '" + tenant.getRealmName() + "': " + rootCause);
        }

        self.saveTenantStatus(tenant, "REALM_CREATED");
        self.saveProvisionSteps(tenant,
                ProvisionStep.markCompleted(tenant.getProvisionStepsCompleted(), ProvisionStep.REALM_CREATED));
    }

    /**
     * Ensure Keycloak OIDC client exists for tenant.
     * DB status save is committed before Keycloak calls to avoid holding a connection during I/O.
     *
     * For default tenant creation: if client already exists in Keycloak, bind it instead of failing.
     */
    private void ensureClient(Tenant tenant, CreateTenantRequest req) {

        if (kcUtil.clientExists(tenant.getRealmName(), tenant.getTenantName())) {
            log.info("Client '{}' already exists in realm '{}'. Binding to tenant.",
                    tenant.getTenantName(), tenant.getRealmName());
            self.saveTenantStatus(tenant, "CLIENT_CREATED");
            return;
        }

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(tenant.getTenantName());
        client.setName(tenant.getTenantName());
        client.setProtocol("openid-connect");
        client.setPublicClient(true);
        client.setRedirectUris(
                List.of(
                        normalizeDomainForRedirect(req.getDomain()),
                        "https://bmbddaddkbojngoeipadffgalnopmnkc.chromiumapp.org/",
                        "https://gcmkafkcejljlkgfkkkpidmeenmkfpdf.chromiumapp.org/"
                )
        );
        client.setWebOrigins(List.of("*"));
        client.setStandardFlowEnabled(true);
        client.setEnabled(true);

        try {
            kcUtil.createClient(tenant.getRealmName(), client);
        } catch (Exception e) {
            // Check if client was created by another process (race condition or default tenant resume)
            if (kcUtil.clientExists(tenant.getRealmName(), tenant.getTenantName())) {
                log.info("Client '{}' created by another process or already exists in realm '{}'. Binding to tenant.",
                        tenant.getTenantName(), tenant.getRealmName());
            } else {
                throw new GlobalException("CLIENT_CREATION_FAILED", e.getMessage());
            }
        }

        self.saveTenantStatus(tenant, "CLIENT_CREATED");
        self.saveProvisionSteps(tenant,
                ProvisionStep.markCompleted(tenant.getProvisionStepsCompleted(), ProvisionStep.CLIENT_CREATED));
    }

    /**
     * Ensure default admin user exists in Keycloak.
     * Links existing users when found.
     */
    private void ensureAdminUser(Tenant tenant, CreateTenantRequest req) {

        List<User> users = tenant.getUsers();
        if (users == null || users.isEmpty()) {
            throw new GlobalException(
                    "ADMIN_USER_NOT_FOUND",
                    "Tenant has no users: " + tenant.getTenantName()
                            + ". Tenant data may be corrupted — manual investigation required."
            );
        }

        User admin = users.stream()
                .filter(User::isDefaultUser)
                .findFirst()
                .orElseThrow(() -> new GlobalException(
                        "ADMIN_USER_NOT_FOUND",
                        "No default admin user found for tenant: " + tenant.getTenantName()
                                + ". Tenant data may be corrupted — manual investigation required."
                ));

        // 🔐 DB guard – already created in Keycloak
        if (admin.getKeycloakUserId() != null) {
            self.saveTenantStatus(tenant, "USER_CREATED");
            return;
        }

        // 🔍 KC lookup by email OR username
        List<UserRepresentation> existing =
                kcUtil.findUsersByUsernameOrEmail(
                        tenant.getRealmName(),
                        admin.getUserName(),
                        admin.getEmail()
                );

        String kcUserId;
        if (!existing.isEmpty()) {
            // Validate the matched user actually corresponds to our admin
            UserRepresentation matched = existing.stream()
                    .filter(u -> admin.getEmail().equalsIgnoreCase(u.getEmail())
                            || admin.getUserName().equalsIgnoreCase(u.getUsername()))
                    .findFirst()
                    .orElse(null);

            if (matched != null) {
                kcUserId = matched.getId();
                log.info("ensureAdminUser: matched existing KC user id={} email={} for tenant={}",
                        kcUserId, matched.getEmail(), tenant.getTenantName());
            } else {
                log.warn("ensureAdminUser: KC returned users but none matched admin email={} or username={}. Creating new user.",
                        admin.getEmail(), admin.getUserName());
                kcUserId = kcUtil.createUser(
                        tenant.getRealmName(),
                        admin.getUserName(),
                        admin.getEmail(),
                        admin.getFirstName(),
                        admin.getLastName(),
                        false
                );
            }
        } else {
            kcUserId = kcUtil.createUser(
                    tenant.getRealmName(),
                    admin.getUserName(),
                    admin.getEmail(),
                    admin.getFirstName(),
                    admin.getLastName(),
                    false
            );
        }

        // 🔐 Ensure KC realm-admin (Keycloak side)
        kcUtil.assignRealmAdminRoleIfMissing(
                tenant.getRealmName(), kcUserId
        );

        // 🔐 Persist KC linkage — short transaction, releases connection before group/role ops
        admin.setKeycloakUserId(kcUserId);
        admin.setStatus("ACTIVE");
        self.saveUser(admin, tenant.getTenantName());

        // =========================================================
        // 🔑 APPLICATION SIDE (roles & groups)
        // =========================================================

        int steps = tenant.getProvisionStepsCompleted();

        // 1️⃣ Resolve all admin roles for this tenant type
        // selfManaged MSSP/Master MSSP tenants get both their primary role AND ENTERPRISE ADMIN
        List<Roles> adminRoles =
                roleService.resolveAdminRolesForTenant(
                        tenant,
                        admin.getPkUserId()
                );
        List<Roles> validRoles = adminRoles.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!validRoles.isEmpty()) {
            steps = ProvisionStep.markCompleted(steps, ProvisionStep.ROLES_CREATED);
        }

        // 2️⃣ Ensure default admin group exists
        String adminGroupName = tenant.getTenantName() + "_Admin";

        Groups adminGroup =
                groupService.createOrGetDefaultGroup(
                        tenant.getTenantID(),
                        adminGroupName,
                        true,
                        admin.getPkUserId()
                );
        steps = ProvisionStep.markCompleted(steps, ProvisionStep.ADMIN_GROUP_CREATED);

        // 3️⃣ Roles → Group (IDEMPOTENT) — assign every resolved role
        validRoles.forEach(role -> groupService.assignRoleToGroupIfMissing(adminGroup, role));

        // 4️⃣ User → Group — direct SQL with ON CONFLICT DO NOTHING.
        // Bypasses JPA collection management to avoid Hibernate duplicate insert
        // when saveProvisionSteps triggers a flush of dirty admin.mappedGroups.
        userRepository.insertUserGroupMappingIfAbsent(
                admin.getPkUserId(), adminGroup.getPkGroupId());
        log.info("ensureAdminUser: user-group mapping ensured. userId={} groupId={}",
                admin.getPkUserId(), adminGroup.getPkGroupId());

        steps = ProvisionStep.markCompleted(steps, ProvisionStep.USER_GROUP_LINKED);
        steps = ProvisionStep.markCompleted(steps, ProvisionStep.ADMIN_USER_CREATED);
        self.saveProvisionSteps(tenant, steps);

        // Status update moved to caller (resumeTenantSetup) to avoid REQUIRES_NEW transaction conflict
    }



    private void finalizeTenant(Tenant tenant, CreateTenantRequest req) {

        int steps = tenant.getProvisionStepsCompleted();

        // ── SSO setup (must succeed before ACTIVE) ──
        if (!ProvisionStep.isCompleted(steps, ProvisionStep.SSO_CONFIGURED)) {
            if ("AZURE".equalsIgnoreCase(req.getSsoType())) {
                setupHubAndSpokeSSO(tenant, req);
            }
            if ("APIKEY".equalsIgnoreCase(req.getSsoType())) {
                tenantIdApiKeys.put(tenant.getTenantID(), setupApiKeySSO(tenant));
            }
            steps = ProvisionStep.markCompleted(steps, ProvisionStep.SSO_CONFIGURED);
            self.saveProvisionSteps(tenant, steps);
        }

        // ── Tenant code + login URL ──
        if (!ProvisionStep.isCompleted(steps, ProvisionStep.TENANT_CODE_GENERATED)) {
            if (tenant.getTenantCode() == null || tenant.getTenantCode().isBlank()) {
                tenant.setTenantCode(generateUniqueTenantCode(req.getTenantName()));
            }
            tenant.setLoginUrl(generateLoginUrl(req));
            // Persist tenantCode + loginUrl immediately via saveTenantStatus so they survive
            // a failure in later steps (auth config, policies). On retry the bitmask would
            // skip this block, but the DB would still have null values without this save.
            self.saveTenantStatus(tenant, tenant.getStatus());
            steps = ProvisionStep.markCompleted(steps, ProvisionStep.TENANT_CODE_GENERATED);
            self.saveProvisionSteps(tenant, steps);
        }

        // ── Auth provider config (BEFORE marking ACTIVE) ──
        if (!ProvisionStep.isCompleted(steps, ProvisionStep.AUTH_CONFIG_SAVED)) {
            self.saveAuthProviderConfig(tenant, req);
            steps = ProvisionStep.markCompleted(steps, ProvisionStep.AUTH_CONFIG_SAVED);
            self.saveProvisionSteps(tenant, steps);
        }

        // ── Mark ACTIVE ──
        self.saveTenantStatus(tenant, "ACTIVE");

        // ── Default policies ──
        if (!ProvisionStep.isCompleted(steps, ProvisionStep.POLICIES_CREATED)) {
            try {
                browserPolicyService.createTenantDefaultPolicy(tenant.getTenantID());
                networkPolicyService.createTenantDefaultPolicy(tenant.getTenantID());
                extensionPolicyService.createTenantDefaultPolicy(tenant.getTenantID());
                steps = ProvisionStep.markCompleted(steps, ProvisionStep.POLICIES_CREATED);
                self.saveProvisionSteps(tenant, steps);
                log.info("Default policies created for tenant: {}", tenant.getTenantName());
            } catch (Exception e) {
                log.warn("Failed to create default policies for tenant {}: {}",
                        tenant.getTenantName(), e.getMessage());
            }
        }

        // ── Default EventsGroup ──
        if (!ProvisionStep.isCompleted(steps, ProvisionStep.EVENTS_GROUP_CREATED)) {
            boolean needsGroup = Boolean.TRUE.equals(tenant.getSelfManaged())
                    || "APIKEY".equalsIgnoreCase(req.getSsoType());
            if (needsGroup) {
                try {
                    EventsGroup defaultGroup = eventsGroupService.createDefaultGroupForTenant(
                            tenant.getTenantID(), "SYSTEM");

                    // APIKEY: auto-assign policies to default group
                    if ("APIKEY".equalsIgnoreCase(req.getSsoType())
                            && policyAssignmentRepository.countByTenantId(tenant.getTenantID()) == 0) {
                        BrowserPolicy bp = browserPolicyService.createTenantDefaultPolicy(tenant.getTenantID());
                        NetworkPolicy np = networkPolicyService.createTenantDefaultPolicy(tenant.getTenantID());
                        ExtensionPolicy ep = extensionPolicyService.createTenantDefaultPolicy(tenant.getTenantID());

                        policyAssignmentRepository.saveAll(List.of(
                                PolicyAssignment.builder()
                                        .browserPolicy(bp).networkPolicy(null).extensionPolicy(null)
                                        .azureResourceId(defaultGroup.getPkEventsGroupId())
                                        .azureResourceName(defaultGroup.getName())
                                        .assignmentType("GROUP").tenantId(tenant.getTenantID())
                                        .assignedAt(LocalDateTime.now()).build(),
                                PolicyAssignment.builder()
                                        .browserPolicy(null).networkPolicy(np).extensionPolicy(null)
                                        .azureResourceId(defaultGroup.getPkEventsGroupId())
                                        .azureResourceName(defaultGroup.getName())
                                        .assignmentType("GROUP").tenantId(tenant.getTenantID())
                                        .assignedAt(LocalDateTime.now()).build(),
                                PolicyAssignment.builder()
                                        .browserPolicy(null).networkPolicy(null).extensionPolicy(ep)
                                        .azureResourceId(defaultGroup.getPkEventsGroupId())
                                        .azureResourceName(defaultGroup.getName())
                                        .assignmentType("GROUP").tenantId(tenant.getTenantID())
                                        .assignedAt(LocalDateTime.now()).build()
                        ));
                    }
                    steps = ProvisionStep.markCompleted(steps, ProvisionStep.EVENTS_GROUP_CREATED);
                    self.saveProvisionSteps(tenant, steps);
                } catch (Exception e) {
                    log.warn("Failed to create default events group for tenant {}: {}",
                            tenant.getTenantName(), e.getMessage());
                }
            } else {
                steps = ProvisionStep.markCompleted(steps, ProvisionStep.EVENTS_GROUP_CREATED);
                self.saveProvisionSteps(tenant, steps);
            }
        }

        // ── Realm settings (NON-FATAL) ──
        if (!ProvisionStep.isCompleted(steps, ProvisionStep.REALM_SETTINGS_APPLIED)) {
            try {
                kcUtil.updateRealmTokenSettings(
                        tenant.getRealmName(),
                        3600, 7200, 28800
                );
                kcUtil.enableForgotPassword(tenant.getRealmName());
                kcUtil.updateRealmPasswordPolicy(
                        tenant.getRealmName(),
                        6, 18, 1, 1, 1, 1,
                        true, true,
                        30, 90, true
                );
                steps = ProvisionStep.markCompleted(steps, ProvisionStep.REALM_SETTINGS_APPLIED);
                self.saveProvisionSteps(tenant, steps);
            } catch (Exception e) {
                log.warn("Post-activation realm configuration failed for realm={}, continuing",
                        tenant.getRealmName(), e);
            }
        }


        final String adminEmail = req.getAdminEmail();
        final String loginUrl = tenant.getLoginUrl();
        final String tenantDisplayName = tenant.getTenantName();
        final String setPasswordUrl = buildSetPasswordUrl(tenant);
        final User adminUser = tenant.getUsers().stream()
                .filter(User::isDefaultUser)
                .findFirst()
                .orElse(null);
        final boolean welcomeAlreadySent = adminUser != null && adminUser.getWelcomeEmailSentAt() != null;

        if (welcomeAlreadySent) {
            log.info("[EMAIL] Welcome email already sent for tenant '{}', skipping duplicate send.", tenantDisplayName);
        } else {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                sendWithRetry(
                        () -> kcUtil.sendWelcomeEmail(adminEmail, loginUrl, tenantDisplayName, setPasswordUrl),
                        "Welcome email", adminEmail, 3, 2000
                );
                if (adminUser != null) {
                    adminUser.setWelcomeEmailSentAt(Instant.now());
                    adminUser.setWelcomeEmailSentCount(adminUser.getWelcomeEmailSentCount() + 1);
                    userRepository.save(adminUser);
                }
            });
        }

        // 📦 Package assignment via IAM API (NON-FATAL)
        assignTenantPackage(tenant, req);
    }

    /**
     * Retry a Runnable up to {@code maxRetries} times with exponential backoff.
     * Used for non-critical async operations like email sending.
     */
    private void sendWithRetry(Runnable action, String label, String context, int maxRetries, long initialDelayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                log.info("{} sent successfully for {}", label, context);
                return;
            } catch (Exception e) {
                log.warn("{} attempt {}/{} failed for {}: {}",
                        label, attempt, maxRetries, context, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(initialDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("{} retry interrupted for {}", label, context);
                        return;
                    }
                } else {
                    log.error("{} failed after {} attempts for {}. Manual intervention may be needed.",
                            label, maxRetries, context);
                }
            }
        }
    }

    private String setupApiKeySSO(Tenant tenant) {

        log.info("[API-KEY] Provisioning API Key for tenant={}", tenant.getTenantName());

        try {

            // 🚫 Prevent duplicate active keys
            boolean activeExists =
                    apiKeyRepository.existsByTenantIdAndStatus(
                            tenant.getTenantID(),
                            "ACTIVE"
                    );

            if (activeExists) {
                throw new RuntimeException(
                        "Active API key already exists. Use rotation."
                );
            }

            // 1️⃣ Generate secure API key
            String rawKey = generateUniqueApiKey(26);
            String hashedKey = ApiKeyUtil.hash(rawKey);
            String keyPrefix = rawKey.substring(0, 8);

            // 2️⃣ Prepare extension client id
            String clientId = tenant.getTenantName()
                    .toLowerCase()
                    .replaceAll("\\s+", "-") + "-extension-client";

            ClientRepresentation extensionClient;

            // 3️⃣ Ensure Keycloak client exists
            if (kcUtil.clientExists(tenant.getRealmName(), clientId)) {

                extensionClient =
                        kcUtil.getClientWithSecret(
                                tenant.getRealmName(),
                                clientId
                        );

            } else {

                ClientRepresentation client = new ClientRepresentation();
                client.setClientId(clientId);
                client.setServiceAccountsEnabled(true);
                client.setPublicClient(false);
                client.setStandardFlowEnabled(false);
                client.setImplicitFlowEnabled(false);
                client.setDirectAccessGrantsEnabled(false);
                client.setRedirectUris(
                        List.of(normalizeDomainForRedirect(tenant.getDomain()))
                );

                kcUtil.createClient(tenant.getRealmName(), client);

                extensionClient =
                        kcUtil.getClientWithSecret(
                                tenant.getRealmName(),
                                clientId
                        );
            }

            // 4️⃣ Encrypt client secret
            String encryptedSecret =
                    CryptoUtil.encrypt(extensionClient.getSecret());

            // 5️⃣ Persist API key metadata
            ExtensionApiKey apiKey = ExtensionApiKey.builder()
                    .tenantId(tenant.getTenantID())
                    .keyHash(hashedKey)
                    .keyPrefix(keyPrefix)
                    .clientId(clientId)
                    .clientSecret(encryptedSecret)
                    .expiresAt(LocalDateTime.now().plusDays(90))
                    .status("ACTIVE")
                    .build();

            apiKeyRepository.save(apiKey);

            log.info("[API-KEY] API Key created successfully for tenant={}",
                    tenant.getTenantName());

            // 🔐 RETURN RAW KEY ONLY ONCE
            return rawKey;

        } catch (Exception e) {
            log.error("[API-KEY] Provisioning failed for tenant={}",
                    tenant.getTenantName(), e);

            throw new RuntimeException(
                    "API_KEY_PROVISIONING_FAILED", e
            );
        }
    }


    public String generateUniqueApiKey(int length) {
        // Defined character set: Uppercase, Lowercase, Numbers, and select Special Chars
        // Removed: '-' and '_'
        final String candidateChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "abcdefghijklmnopqrstuvwxyz" +
                "0123456789" +
                "!@#$%^&*()=+[]{};:,.<>?";

        SecureRandom secureRandom = new SecureRandom();

        return secureRandom.ints(length, 0, candidateChars.length())
                .mapToObj(candidateChars::charAt)
                .map(Object::toString)
                .collect(Collectors.joining());
    }

    /**
     * Create subscription for tenant using direct database access.
     *
     * Flow:
     * 1. If packageId + billingCycleId provided → Create subscription with those
     * 2. If only packageId provided → Create subscription with default billing cycle
     * 3. If startTrial=true → Create trial subscription
     * 4. If nothing provided → Create default Freemium subscription
     */
    private void assignTenantPackage(Tenant tenant, CreateTenantRequest req) {
        try {
            SubscriptionSummary subscription;

            if (req.getPackageId() != null && req.getBillingCycleId() != null) {
                // Full subscription with package and billing cycle
                subscription = subscriptionService.createSubscription(
                        tenant.getTenantID(),
                        req.getPackageId(),
                        req.getBillingCycleId(),
                        req.getStartTrial(),
                        tenant.getCreatedBy()
                );
                log.info("Subscription created for tenant={}: package={}, billingCycle={}, trial={}",
                        tenant.getTenantName(), req.getPackageId(), req.getBillingCycleId(), req.getStartTrial());
            } else if (req.getPackageId() != null) {
                // Package provided but no billing cycle - use default MONTHLY billing cycle
                Long billingCycleId = subscriptionService.findBillingCycleIdByCode("MONTHLY")
                        .orElse(null);

                if (billingCycleId != null) {
                    subscription = subscriptionService.createSubscription(
                            tenant.getTenantID(),
                            req.getPackageId(),
                            billingCycleId,
                            req.getStartTrial(),
                            tenant.getCreatedBy()
                    );
                    log.info("Subscription created for tenant={} with default billing cycle",
                            tenant.getTenantName());
                } else {
                    log.warn("No default billing cycle found, creating default subscription for tenant={}",
                            tenant.getTenantName());
                    subscription = subscriptionService.createDefaultSubscription(
                            tenant.getTenantID(),
                            tenant.getCreatedBy()
                    );
                }
            } else {
                // No package provided - create default Freemium subscription
                subscription = subscriptionService.createDefaultSubscription(
                        tenant.getTenantID(),
                        tenant.getCreatedBy()
                );
                log.info("Default subscription created for tenant={}", tenant.getTenantName());
            }

            if (subscription != null) {
                log.info("Subscription active for tenant={}: package={}, status={}, trial={}",
                        tenant.getTenantName(),
                        subscription.getPackageName(),
                        subscription.getStatus(),
                        subscription.getIsTrial());
            } else {
                log.warn("Subscription creation returned null for tenant={}, tenant may need manual subscription setup",
                        tenant.getTenantName());
            }
        } catch (Exception e) {
            log.warn("Subscription creation failed for tenant={}, continuing: {}",
                    tenant.getTenantName(), e.getMessage());
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveAuthProviderConfig(Tenant tenant, CreateTenantRequest req) {
        log.info("saveAuthProviderConfig: saving auth provider config if not exists. tenantId={}", tenant.getTenantID());

        // Re-fetch tenant inside this REQUIRES_NEW transaction to avoid detached entity
        // issues when the caller's persistence context has dirty/lazy associations
        // (e.g. selfManaged tenants with extra role-group mappings from ensureAdminUser).
        Tenant managedTenant = tenantRepository.findByTenantID(tenant.getTenantID())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenant.getTenantID()));

        if (authProviderConfigRepository.findByTenant(managedTenant).isPresent()) {
            log.info("Auth provider config already exists for tenantId={}, skipping.", tenant.getTenantID());
            return;
        }

        String redirectUri = normalizeDomainForRedirect(req.getDomain());
        String loginUrl = generateLoginUrl(req);
        log.debug("Using redirectUri={} and loginUrl={} for auth config. tenantId={}",
                redirectUri, loginUrl, tenant.getTenantID());

        // Determine SSO type: APIKEY, AZURE, or default to KEYCLOAK
        String ssoType = determineSsoType(req.getSsoType());

        AuthProviderConfig cfg = new AuthProviderConfig();
        cfg.setTenant(managedTenant);
        cfg.setSsoType(ssoType);
        cfg.setIssuerUri(baseUrl + "/realms/" + tenant.getRealmName());
        cfg.setAuthServerUrl(baseUrl);
        cfg.setTokenEndpoint(baseUrl + "/realms/" + tenant.getRealmName() + "/protocol/openid-connect/token");
        cfg.setJwkUri(baseUrl + "/realms/" + tenant.getRealmName() + "/protocol/openid-connect/certs");
        cfg.setClientId(tenant.getTenantName());
        cfg.setRedirectUri(redirectUri);
        cfg.setLoginUrl(loginUrl);
        cfg.setScopes("openid profile email");

        authProviderConfigRepository.save(cfg);
        log.info("Auth provider config saved successfully with ssoType={}. tenantId={}", ssoType, tenant.getTenantID());
    }


    private String determineSsoType(String requestedSsoType) {
        if (requestedSsoType == null || requestedSsoType.isBlank()) {
            return "KEYCLOAK";
        }

        String normalized = requestedSsoType.toUpperCase().trim();
        if ("APIKEY".equals(normalized) || "AZURE".equals(normalized)) {
            return normalized;
        }

        return "KEYCLOAK";
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateAuthProviderConfig(Tenant tenant, String ssoType) {
        log.info("updateAuthProviderConfig: tenantId={} ssoType={}", tenant.getTenantID(), ssoType);

        String effectiveSsoType = (ssoType != null) ? ssoType.toUpperCase() : "KEYCLOAK";

        String redirect = "https://" + normalizeDomainForDB(tenant.getDomain());
        StringBuilder loginUrlBuilder = new StringBuilder(baseUrl)
                .append("/realms/").append(tenant.getRealmName())
                .append("/protocol/openid-connect/auth?client_id=").append(tenant.getTenantName())
                .append("&redirect_uri=").append(URLEncoder.encode(redirect, StandardCharsets.UTF_8))
                .append("&response_type=code");

        if ("AZURE".equalsIgnoreCase(effectiveSsoType)) {
            loginUrlBuilder.append("&kc_idp_hint=master-hub");
        }
        String loginUrl = loginUrlBuilder.toString();

        authProviderConfigRepository.findByTenant(tenant).ifPresentOrElse(
                cfg -> {
                    cfg.setSsoType(effectiveSsoType);
                    cfg.setLoginUrl(loginUrl);
                    authProviderConfigRepository.save(cfg);
                    log.info("AuthProviderConfig updated for tenantId={}", tenant.getTenantID());
                },
                () -> log.warn("No AuthProviderConfig found for tenantId={}, skipping update", tenant.getTenantID())
        );

        // Also persist the login URL on the tenant itself
        tenant.setLoginUrl(loginUrl);
        tenantRepository.save(tenant);
    }


    private void reconcileTenantMutableFields(
            Tenant tenant,
            CreateTenantRequest req
    ) {
        boolean changed = false;

        if (!Objects.equals(tenant.getPhoneNo(), req.getPhoneNo())) {
            tenant.setPhoneNo(req.getPhoneNo());
            changed = true;
        }

        String normalizedDomain = normalizeDomainForDB(req.getDomain());
        if (!Objects.equals(tenant.getDomain(), normalizedDomain)) {
            tenant.setDomain(normalizedDomain);
            changed = true;
        }

        if (!Objects.equals(tenant.getRegion(), req.getRegion())) {
            tenant.setRegion(req.getRegion());
            changed = true;
        }

        if (changed) {
            self.persistTenantSkeleton(tenant, tenant.getTenantName());
            log.info("Tenant mutable fields reconciled for tenant={}", tenant.getTenantName());
        }
    }

    private void reconcileAdminMutableFields(
            Tenant tenant,
            CreateTenantRequest req
    ) {
        User admin = userRepository
                .findDefaultAdminByTenantId(tenant.getTenantID())
                .orElseThrow(() ->
                        new IllegalStateException("Default admin not found for tenant " + tenant.getTenantName())
                );

        boolean changed = false;

        if (!Objects.equals(admin.getPhoneNo(), req.getAdminPhoneNumber())) {
            admin.setPhoneNo(req.getAdminPhoneNumber());
            changed = true;
        }

        if (!Objects.equals(admin.getFirstName(), req.getAdminFirstName())) {
            admin.setFirstName(req.getAdminFirstName());
            changed = true;
        }

        if (!Objects.equals(admin.getLastName(), req.getAdminLastName())) {
            admin.setLastName(req.getAdminLastName());
            changed = true;
        }

        if (changed) {
            userRepository.save(admin);
            log.info("Admin mutable fields reconciled for tenant={}", tenant.getTenantName());
        }
    }




    @Transactional
    public Tenant updateTenant(
            HttpServletRequest request,
            String id,
            Tenant req,
            Boolean syncKeycloak
    ) {

        Tenant current = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));

        ensureRequesterIsParentOrSelf(request, current);

        // 🔒 Enforce immutability
        enforceImmutableTenantFields(current, req);

        String previousStatus = current.getStatus();
        String newStatus = previousStatus;

        // -------------------------
        // STATUS UPDATE
        // -------------------------
        if (req.getStatus() != null) {
            newStatus = req.getStatus().toUpperCase();
            Set<String> allowed = Set.of("ACTIVE", "INACTIVE");

            if (!allowed.contains(newStatus)) {
                throw new KeycloakOperationException(
                        "INVALID_STATUS_UPDATE",
                        1041,
                        "Unsupported status change"
                );
            }
            current.setStatus(newStatus);
        }

        // -------------------------
        // SAFE FIELDS ONLY
        // -------------------------
        if (req.getPhoneNo() != null) current.setPhoneNo(req.getPhoneNo());
        if (req.getIndustry() != null) current.setIndustry(req.getIndustry());
        if (req.getRegion() != null) current.setRegion(req.getRegion());
        if (req.getTemporaryAddress() != null && current.getTemporaryAddress() != null) {
            copyAddress(current.getTemporaryAddress(), req.getTemporaryAddress());
        }
        if (req.getPermanentAddress() != null && current.getPermanentAddress() != null) {
            copyAddress(current.getPermanentAddress(), req.getPermanentAddress());
        }
        if (req.getBillingAddress() != null && current.getBillingAddress() != null) {
            copyAddress(current.getBillingAddress(), req.getBillingAddress());
        }
        if (req.getBillingCycleType() != null) current.setBillingCycleType(req.getBillingCycleType());

        User updater = jwtUtl.getUserFromRequest(request);
        current.setUpdatedBy(updater.getPkUserId());
        current.setUpdatedAt(LocalDateTime.now());

        tenantRepository.save(current);

        // -------------------------
        // KEYCLOAK SYNC (STATUS ONLY)
        // -------------------------
        if (Boolean.TRUE.equals(syncKeycloak)
                && !Objects.equals(previousStatus, newStatus)) {

            syncTenantStatusWithKeycloak(current, newStatus);
        }

        return current;
    }


    @Transactional
    public TenantResponse updateTenantWithSubscription(
            HttpServletRequest request,
            String id,
            UpdateTenantRequest req,
            Boolean syncKeycloak
    ) {
        log.info("updateTenantWithSubscription: tenantId={}, packageId={}", id, req.getPackageId());

        // Eager-load users to avoid LazyInitializationException in admin update section
        Tenant current = tenantRepository.findByTenantIDWithUsers(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));

        ensureRequesterIsParentOrSelf(request, current);

        String previousStatus = current.getStatus();
        String newStatus = previousStatus;

        // -------------------------
        // STATUS UPDATE
        // -------------------------
        if (req.getStatus() != null) {
            newStatus = req.getStatus().toUpperCase();
            Set<String> allowed = Set.of("ACTIVE", "INACTIVE");

            if (!allowed.contains(newStatus)) {
                throw new KeycloakOperationException(
                        "INVALID_STATUS_UPDATE",
                        1041,
                        "Unsupported status change"
                );
            }
            current.setStatus(newStatus);
        }

        // -------------------------
        // SAFE FIELDS ONLY
        // -------------------------
        if (req.getPhoneNo() != null) current.setPhoneNo(req.getPhoneNo());
        if (req.getIndustry() != null) current.setIndustry(req.getIndustry());
        if (req.getRegion() != null) current.setRegion(req.getRegion());
        if (req.getTemporaryAddress() != null && current.getTemporaryAddress() != null) {
            copyAddress(current.getTemporaryAddress(), req.getTemporaryAddress());
        }
        if (req.getPermanentAddress() != null && current.getPermanentAddress() != null) {
            copyAddress(current.getPermanentAddress(), req.getPermanentAddress());
        }
        if (req.getBillingAddress() != null && current.getBillingAddress() != null) {
            copyAddress(current.getBillingAddress(), req.getBillingAddress());
        }
        if (req.getBillingCycleType() != null) current.setBillingCycleType(req.getBillingCycleType());

        // -------------------------
        // SELF-MANAGED TOGGLE
        // -------------------------
        if (req.getSelfManaged() != null) {
            handleSelfManagedChange(current, req.getSelfManaged(), request);
        }

        // -------------------------
        // SSO TYPE UPDATE
        // -------------------------
        if (req.getSsoType() != null && !req.getSsoType().trim().isEmpty()) {
            AuthProviderConfig currentConfig = authProviderConfigRepository
                    .findByTenant_TenantID(current.getTenantID())
                    .orElse(null);

            String oldSsoType = currentConfig != null ? currentConfig.getSsoType() : null;
            String newSsoType = req.getSsoType().toUpperCase();

            // Only proceed if SSO type is actually changing
            if (oldSsoType == null || !oldSsoType.equalsIgnoreCase(newSsoType)) {
                log.info("🔄 SSO type change requested for tenant {}: {} -> {}",
                        current.getTenantID(), oldSsoType, newSsoType);

                // Handle SSO type change with full provisioning/deprovisioning
                handleSsoTypeChange(current, oldSsoType, newSsoType, req);
            } else {
                log.info("ℹ️ SSO type unchanged for tenant {}: {} (skipping reconfiguration)",
                        current.getTenantID(), oldSsoType);
            }
        }

        User updater = jwtUtl.getUserFromRequest(request);
        current.setUpdatedBy(updater.getPkUserId());
        current.setUpdatedAt(LocalDateTime.now());

        tenantRepository.save(current);

        // -------------------------
        // KEYCLOAK SYNC (STATUS ONLY)
        // -------------------------
        if (Boolean.TRUE.equals(syncKeycloak)
                && !Objects.equals(previousStatus, newStatus)) {
            syncTenantStatusWithKeycloak(current, newStatus);
        }

        // -------------------------
        // ADMIN USER UPDATE (DB + KEYCLOAK)
        // -------------------------
        boolean adminChanged = req.getAdminFirstName() != null
                || req.getAdminLastName() != null
                || req.getAdminPhoneNumber() != null;

        if (adminChanged) {
            try {
                User admin = current.getUsers().stream()
                        .filter(User::isDefaultUser)
                        .findFirst()
                        .orElse(null);

                if (admin != null) {
                    if (req.getAdminFirstName() != null)  admin.setFirstName(req.getAdminFirstName());
                    if (req.getAdminLastName() != null)   admin.setLastName(req.getAdminLastName());
                    if (req.getAdminPhoneNumber() != null) admin.setPhoneNo(req.getAdminPhoneNumber());
                    self.saveUser(admin, current.getTenantName());
                    log.info("Admin user DB updated for tenant={}", current.getTenantName());

                    // Sync to Keycloak if keycloakUserId is available
                    if (admin.getKeycloakUserId() != null) {
                        kcUtil.updateKeycloakUser(
                                current.getRealmName(),
                                admin.getKeycloakUserId(),
                                req.getAdminFirstName(),
                                req.getAdminLastName(),
                                null // email is immutable
                        );
                        log.info("Admin user Keycloak updated for tenant={}", current.getTenantName());
                    }
                } else {
                    log.warn("No default admin user found for tenant={}, skipping admin update", current.getTenantName());
                }
            } catch (Exception e) {
                log.warn("Admin user update failed for tenant={}: {}", current.getTenantName(), e.getMessage());
            }
        }

        // -------------------------
        // SUBSCRIPTION/PACKAGE UPDATE
        // -------------------------
        if (req.getPackageId() != null) {
            try {
                String updatedBy = updater != null ? updater.getUserName() : null;
                SubscriptionSummary subscription;

                // Check if tenant has active subscription
                if (subscriptionService.hasActiveSubscription(current.getTenantID())) {
                    // Upgrade existing subscription
                    subscription = subscriptionService.upgradeSubscription(
                            current.getTenantID(),
                            req.getPackageId(),
                            req.getBillingCycleId(),
                            updatedBy
                    );
                    log.info("Subscription upgraded for tenant={} to packageId={}, newStatus={}",
                            current.getTenantName(), req.getPackageId(), subscription.getStatus());
                } else {
                    // Create new subscription
                    subscription = subscriptionService.createSubscription(
                            current.getTenantID(),
                            req.getPackageId(),
                            req.getBillingCycleId(),
                            req.getStartTrial(), // use startTrial from request
                            updatedBy
                    );
                    log.info("Subscription created for tenant={} with packageId={}, trial={}, status={}",
                            current.getTenantName(), req.getPackageId(), req.getStartTrial(), subscription.getStatus());
                }
            } catch (Exception e) {
                log.warn("Subscription update failed for tenant={}: {}",
                        current.getTenantName(), e.getMessage());
                throw new GlobalException("SUBSCRIPTION_UPDATE_FAILED",
                        "Failed to update subscription: " + e.getMessage());
            }
        }

        return buildResponse(current);
    }

    private void enforceImmutableTenantFields(Tenant current, Tenant incoming) {

        if (incoming.getTenantName() != null &&
                !Objects.equals(current.getTenantName(), incoming.getTenantName())) {
            throw new KeycloakOperationException(
                    "TENANT_NAME_IMMUTABLE",
                    1040,
                    "Tenant name cannot be changed after provisioning"
            );
        }

        if (incoming.getRealmName() != null &&
                !Objects.equals(current.getRealmName(), incoming.getRealmName())) {
            throw new KeycloakOperationException(
                    "REALM_IMMUTABLE",
                    1042,
                    "Realm name cannot be changed"
            );
        }

        if (incoming.getDomain() != null &&
                !Objects.equals(current.getDomain(), normalizeDomainForDB(incoming.getDomain()))) {
            throw new KeycloakOperationException(
                    "DOMAIN_IMMUTABLE",
                    1043,
                    "Domain cannot be changed after provisioning"
            );
        }

        if (incoming.getEmail() != null &&
                !Objects.equals(current.getEmail(), incoming.getEmail())) {
            throw new KeycloakOperationException(
                    "TENANT_EMAIL_IMMUTABLE",
                    1044,
                    "Tenant email cannot be changed"
            );
        }
    }

    private void syncTenantStatusWithKeycloak(Tenant tenant, String newStatus) {

        String realm = tenant.getRealmName();

        switch (newStatus) {

            case "ACTIVE" -> {
                kcUtil.setRealmEnabled(realm, true);
                kcUtil.setAllUsersEnabled(realm, true);
            }

            case "INACTIVE" -> {
                kcUtil.setRealmEnabled(realm, true);
                kcUtil.setAllUsersEnabled(realm, false);
            }

            default -> throw new KeycloakOperationException(
                    "INVALID_STATUS",
                    1042,
                    "Unsupported tenant status: " + newStatus
            );
        }
    }


    @Transactional
    public void hardDeleteTenant(
            HttpServletRequest request,
            String tenantId,
            DeleteTenantRequest req
    ) {

        log.warn("⚠ HARD DELETE requested for tenantId={}", tenantId);

        // ------------------------------------------------
        // 0️⃣ Load + authorize
        // ------------------------------------------------
        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        ensureRequesterIsParentOrSelf(request, tenant);

        if (!Boolean.TRUE.equals(req.getConfirmDelete())) {
            throw new KeycloakOperationException(
                    "DELETE_CONFIRMATION_REQUIRED",
                    1061,
                    "Explicit delete confirmation required"
            );
        }

        if ("ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new KeycloakOperationException(
                    "TENANT_ACTIVE_DELETE_NOT_ALLOWED",
                    1060,
                    "Deactivate tenant before hard delete"
            );
        }

        String realm = tenant.getRealmName();

        log.warn("🧨 HARD DELETE STARTED tenantId={} realm={}", tenantId, realm);

        // ------------------------------------------------
        // 1️⃣ DELETE KEYCLOAK REALM (FAIL-FAST)
        // ------------------------------------------------
        try {
            kcUtil.deleteRealmHard(realm);
        } catch (Exception e) {
            log.error("❌ Keycloak realm deletion failed. Aborting DB delete. realm={}", realm, e);
            throw new KeycloakOperationException(
                    "KC_REALM_DELETE_FAILED",
                    1055,
                    "Failed to delete Keycloak realm"
            );
        }

        // ------------------------------------------------
        // 2️⃣ DELETE SUBSCRIPTION HISTORY
        // ------------------------------------------------
        try {
            subscriptionService.deleteSubscriptionsByTenantId(tenantId);
            log.info("Subscriptions deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.warn("Failed to delete subscriptions for tenantId={}: {}", tenantId, e.getMessage());
        }

        // ------------------------------------------------
        // 3️⃣ DELETE EVENTS GROUPS
        // ------------------------------------------------
        try {
            eventsGroupService.deleteByTenantId(tenantId);
            log.info("EventsGroups deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.warn("Failed to delete events groups for tenantId={}: {}", tenantId, e.getMessage());
        }

        // ------------------------------------------------
        // 4️⃣ DELETE EXTENSION API KEYS
        // ------------------------------------------------
        try {
            List<ExtensionApiKey> apiKeys = apiKeyRepository.findByTenantId(tenantId);
            if (!apiKeys.isEmpty()) {
                apiKeyRepository.deleteAll(apiKeys);
            }
            log.info("ExtensionApiKeys deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.warn("Failed to delete API keys for tenantId={}: {}", tenantId, e.getMessage());
        }

        // ------------------------------------------------
        // 5️⃣ DELETE AUTH PROVIDER CONFIG
        // ------------------------------------------------
        authProviderConfigRepository.findByTenant(tenant)
                .ifPresent(authProviderConfigRepository::delete);

        // ------------------------------------------------
        // 6️⃣ DELETE GROUPS (AND MAPPINGS)
        // ------------------------------------------------
        groupService.deleteGroupsByTenantId(tenantId);

        // ------------------------------------------------
        // 7️⃣ DELETE ROLES
        // ------------------------------------------------
        roleService.deleteRolesByTenantId(tenantId);

        // ------------------------------------------------
        // 8️⃣ DELETE USERS
        // ------------------------------------------------
        List<User> users = userRepository.findByTenant(tenant);
        if (!users.isEmpty()) {
            userRepository.deleteAll(users);
        }

        // ------------------------------------------------
        // 9️⃣ DELETE TENANT (LAST)
        // ------------------------------------------------
        tenantRepository.delete(tenant);

        log.warn("✅ HARD DELETE COMPLETED tenantId={} realm={}", tenantId, realm);
    }

    /**
     * Best-effort rollback of all resources created during first-time tenant provisioning.
     * Called ONLY from createTenantFirstTime on failure.
     *
     * Cleans up Keycloak realm (cascades all KC children) then removes DB records
     * in reverse dependency order. Each step is independently try-caught so
     * partial cleanup still proceeds without masking the original error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void rollbackFailedCreation(Tenant tenant) {
        if (tenant == null || tenant.getTenantID() == null) {
            log.warn("[ROLLBACK] Skipping rollback — tenant or tenantId is null");
            return;
        }

        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();

        log.warn("[ROLLBACK] Starting best-effort rollback for tenantId={} realm={}", tenantId, realm);

        // Clean up in-memory API key cache to prevent memory leak
        tenantIdApiKeys.remove(tenantId);

        // 1. Delete Keycloak realm (cascades clients, users, IdPs, organizations)
        try {
            kcUtil.deleteRealmHard(realm);
            log.info("[ROLLBACK] KC realm deleted: {}", realm);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete KC realm '{}': {}", realm, e.getMessage());
        }

        // 2. Delete TenantSubscription + SubscriptionHistory (history FK must go first)
        try {
            subscriptionService.deleteSubscriptionsByTenantId(tenantId);
            log.info("[ROLLBACK] Subscriptions deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete Subscriptions for tenantId={}: {}", tenantId, e.getMessage());
        }

        // 3. Delete EventsGroups
        try {
            eventsGroupService.deleteByTenantId(tenantId);
            log.info("[ROLLBACK] EventsGroups deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete EventsGroups for tenantId={}: {}", tenantId, e.getMessage());
        }

        // 4. Delete ExtensionApiKeys
        try {
            List<ExtensionApiKey> keys = apiKeyRepository.findByTenantId(tenantId);
            if (!keys.isEmpty()) {
                apiKeyRepository.deleteAll(keys);
            }
            log.info("[ROLLBACK] ExtensionApiKeys deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete ExtensionApiKeys for tenantId={}: {}", tenantId, e.getMessage());
        }

        // 5. Delete AuthProviderConfig
        try {
            authProviderConfigRepository.findByTenant(tenant)
                    .ifPresent(authProviderConfigRepository::delete);
            log.info("[ROLLBACK] AuthProviderConfig deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete AuthProviderConfig for tenantId={}: {}", tenantId, e.getMessage());
        }

        // 6. Delete Groups (cascades user_group_map + group_role_map)
        try {
            groupService.deleteGroupsByTenantId(tenantId);
            log.info("[ROLLBACK] Groups deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete Groups for tenantId={}: {}", tenantId, e.getMessage());
        }

        // 7. Delete Roles (cascades role_scope_mapping)
        try {
            roleService.deleteRolesByTenantId(tenantId);
            log.info("[ROLLBACK] Roles deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete Roles for tenantId={}: {}", tenantId, e.getMessage());
        }

        // 8. Delete Users
        try {
            List<User> users = userRepository.findByTenant(tenant);
            if (!users.isEmpty()) {
                userRepository.deleteAll(users);
            }
            log.info("[ROLLBACK] Users deleted for tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete Users for tenantId={}: {}", tenantId, e.getMessage());
        }

        // 9. Delete Tenant (LAST — direct JPQL to bypass JPA cascade on already-deleted children)
        try {
            tenantRepository.deleteByTenantIdDirect(tenantId);
            log.info("[ROLLBACK] Tenant deleted: tenantId={}", tenantId);
        } catch (Exception e) {
            log.error("[ROLLBACK] Failed to delete Tenant tenantId={}: {}", tenantId, e.getMessage());
        }

        log.warn("[ROLLBACK] Best-effort rollback completed for tenantId={}", tenantId);
    }

    /**
     * Ensure requester tenant is parent or self of target tenant.
     *
     * @throws KeycloakOperationException if access is denied
     */
    @Transactional
    public Tenant getTenantIfParent(HttpServletRequest request, String id) {

        Tenant requester = jwtUtl.getTenantFromRequest(request);

        Tenant target = tenantRepository.findByTenantID(id)
                .orElseThrow(() -> {
                    log.warn("Tenant not found by ID. tenantId={}", id);
                    return new ResourceNotFoundException("Tenant not found: " + id);
                });

        // ✅ allow if requester is the same tenant
        if (requester != null &&
                requester.getTenantID().equals(target.getTenantID())) {

            log.debug("getTenantIfParent: requester is target, returning tenant");
            populateSsoType(target);
            return target;
        }

        // ✅ traverse upwards from target through parentTenantId chain
        String currentParentId = target.getParentTenantId();

        while (currentParentId != null && !currentParentId.isBlank()) {

            if (requester != null &&
                    currentParentId.equals(requester.getTenantID())) {

                log.debug("getTenantIfParent: requester is ancestor, returning tenant");
                populateSsoType(target);
                return target;
            }

            Optional<Tenant> parentOpt =
                    tenantRepository.findByTenantID(currentParentId);

            if (parentOpt.isEmpty()) {
                break;
            }

            currentParentId = parentOpt.get().getParentTenantId();
        }

        log.warn(
                "Access denied: requester tenantId={} is not an ancestor of tenantId={}",
                requester != null ? requester.getTenantID() : "null",
                id
        );

        throw new KeycloakOperationException(
                ResponseCodes.ACCESS_DENIED,
                1022,
                "Access denied to tenant."
        );
    }

    /**
     * Populates the transient ssoType field from authProviderConfig.
     * Call this before returning a Tenant entity in API responses.
     */
    private void populateSsoType(Tenant tenant) {
        if (tenant != null) {
            try {
                AuthProviderConfig config = authProviderConfigRepository
                        .findByTenant_TenantID(tenant.getTenantID())
                        .orElse(null);
                if (config != null) {
                    tenant.setSsoType(config.getSsoType());
                }
            } catch (Exception e) {
                log.warn("Failed to populate ssoType for tenant {}: {}", tenant.getTenantID(), e.getMessage());
            }
        }
    }

    /**
     * Get full tenant hierarchy under requester tenant.
     *
     * @return list of TenantResponse representing the hierarchy
     */
    public List<TenantResponse> getTenantHierarchy(HttpServletRequest request) {
        Tenant parentTenant = jwtUtl.getTenantFromRequest(request);
        if (parentTenant == null) {
            throw new ResourceNotFoundException("No tenant context found for the current user");
        }
        log.info("getTenantHierarchy: building hierarchy for parentTenantId={}", parentTenant.getTenantID());
        String tenantId = parentTenant.getTenantID();
        List<Tenant> result = new ArrayList<>();

        // Start recursive search
        collectChildren(tenantId, result);

        // Use lightweight response builder to avoid N+1 subscription queries.
        // Each buildResponse() calls subscriptionService.getActiveSubscription() individually.
        // For hierarchy with many children, batch-build without subscription (can be fetched lazily).
        List<TenantResponse> responses = result.stream()
                .map(this::buildResponseLightweight)
                .collect(Collectors.toList());
        log.debug("Converted {} tenants to TenantResponse.", responses.size());
        return responses;
    }

    /**
     * Check if tenant name is available (not present in DB).
     *
     * @param tenantName candidate tenant name
     * @return true if available
     */
    public boolean checkTenantNameAvailability(String tenantName, String tenantId) {
        log.debug("checkTenantNameAvailability tenantName={}, tenantId={}", tenantName, tenantId);

        String normalized = tenantName.toLowerCase();
        if (tenantId == null) {
            return tenantRepository.existsByTenantName(normalized);
        }

        return tenantRepository.existsByTenantNameAndTenantIDNot(normalized, tenantId);
    }


    /**
     * Check if normalized domain exists in DB.
     *
     * @param domain raw domain to check
     * @return true if domain exists
     */
    public boolean checkExistsByDomain(String domain, String tenantId) {
        String domainName = normalizeDomainForDB(domain);

        if (tenantId == null) {
            return tenantRepository.existsByDomain(domainName);
        }

        return tenantRepository.existsByDomainAndTenantIDNot(domainName, tenantId);
    }


    /**
     * Check if a phone number already exists for any tenant.
     *
     * @param phoneNumber phone to check
     * @return true if exists
     */
    public boolean checkPhoneNumber(String phoneNumber, String tenantId) {

        if (tenantId == null) {
            return tenantRepository.existsByPhoneNo(phoneNumber);
        }

        return tenantRepository.existsByPhoneNoAndTenantIDNot(phoneNumber, tenantId);
    }




    public String checkEmail(String email, String tenantId) {

        boolean emailExists = (tenantId == null)
                ? tenantRepository.existsByEmail(email)
                : tenantRepository.existsByEmailAndTenantIDNot(email, tenantId);

        if (emailExists) {
            return "Email already in use by another tenant";
        }

        String subdomain = extractEmailSubdomain(email);
        if (subdomain == null) {
            return null;
        }

        boolean subdomainExists = (tenantId == null)
                ? tenantRepository.existsByEmailSubdomain(subdomain)
                : tenantRepository.existsByEmailSubdomainAndTenantIDNot(subdomain, tenantId);

        if (subdomainExists) {
            return "Email subdomain already in use by another tenant";
        }

        return null;
    }



    private String extractEmailSubdomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }

        String domain = email.substring(email.indexOf('@') + 1)
                .toLowerCase()
                .trim();

        return domain.isBlank() ? null : domain;
    }



    public String createExtensionClient(String tenantId, String redirectUrl, HttpServletRequest request) {

        Tenant requester = jwtUtl.getTenantFromRequest(request);
        if (requester == null) {
            log.warn("createExtensionClient: unauthorized request - no tenant context");
            throw new KeycloakOperationException(ResponseCodes.ACCESS_DENIED, 1022, "Unauthorized tenant");
        }

        String tenantTypeLower = Optional.ofNullable(requester.getTenantType())
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .orElse("");

        boolean isMasterMssp = tenantTypeLower.equals("master mssp")
                || tenantTypeLower.equals("master_mssp")
                || tenantTypeLower.equals("mastermssp");
        boolean isSelfManagedMssp = tenantTypeLower.equals("mssp")
                && Boolean.TRUE.equals(requester.getSelfManaged());

        if (!isMasterMssp && !isSelfManagedMssp) {
            log.warn("Access denied: tenantType={} selfManaged={} cannot create extension client for tenantId={}",
                    requester.getTenantType(), requester.getSelfManaged(), tenantId);
            throw new KeycloakOperationException(ResponseCodes.ACCESS_DENIED, 1022, "Access denied to tenant.");
        }

        Tenant targetTenant = tenantRepository.findByTenantID(tenantId).orElseThrow(() -> {
            log.warn("Tenant not found for creating extension client. tenantId={}", tenantId);
            return new ResourceNotFoundException("Tenant not found: " + tenantId);
        });

        String tenantName = targetTenant.getTenantName().replaceAll("\\s+", "");
        String clientId = tenantName + "Extension";
        String realm = targetTenant.getRealmName();

        try {
            kcUtil.createExtensionClient(realm, clientId, redirectUrl);
            log.info("createExtensionClient: created extension client={} in realm={} with redirectUrl={}", clientId, realm, redirectUrl);
        } catch (Exception e) {
            log.error("Failed to create extension client for realm {}: {}", realm, e.getMessage(), e);
            throw new KeycloakOperationException(ResponseCodes.EXTENSION_CLIENT_CREATION_FAILED, 1030, "Unable to create extension client.");
        }

        return "Extension client '" + clientId + "' successfully created under realm '" + realm + "'";
    }

    /**
     * Build a map of issuer URI -> JwtDecoder for all configured auth providers.
     * Invalid or failing configurations are skipped and logged.
     *
     * @return map of issuer -> JwtDecoder
     */
    public Map<String, JwtDecoder> getJwtDecoders() {
        log.info("Building JWT decoders for all auth provider configs");
        return authProviderConfigRepository.findAll().stream()
                .map(cfg -> {
                    JwtDecoder decoder = createJwtDecoder(cfg);
                    return new AbstractMap.SimpleEntry<>(cfg.getIssuerUri(), decoder);
                })
                .filter(entry -> {
                    boolean keep = entry.getValue() != null;
                    if (!keep) {
                        log.debug("Skipping JWT decoder for issuer {} due to previous errors", entry.getKey());
                    }
                    return keep;
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> replacement // Handle duplicates by preferring the latest
                ));
    }

    /**
     * Create a JwtDecoder for the given provider config. Returns null on failure.
     *
     * @param config provider configuration
     * @return JwtDecoder or null if creation failed
     */
    private JwtDecoder createJwtDecoder(AuthProviderConfig config) {
        try {
            String jwkSetUri = config.getJwkUri() != null && !config.getJwkUri().isBlank()
                    ? config.getJwkUri()
                    : config.getIssuerUri() + "/protocol/openid-connect/certs";

            log.debug("Creating JwtDecoder for issuer={} using jwkSetUri={}", config.getIssuerUri(), jwkSetUri);

            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } catch (Exception e) {
            // Log full exception for troubleshooting and return null to skip this config
            log.error("Failed to create JWT decoder for issuer {}: {}", config.getIssuerUri(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieve tenant configuration and auth provider details by host or company name.
     * Backward-compatible overload that delegates to the full method with null tenantCode.
     *
     * @param host        host value (domain or tenantName), optional
     * @param companyName company name in various formats, optional
     * @return AuthDetailsDto containing auth provider and tenant metadata
     * @throws ResourceNotFoundException when tenant or auth config missing
     */
    @Transactional
    public AuthDetailsDto getTenantConfig(String host, String companyName) {
        return getTenantConfig(host, companyName, null);
    }


    @Transactional
    public AuthDetailsDto getTenantConfig(String host, String companyName, String tenantCode) {
        log.info("Fetching tenant config for host={}, companyName={}, tenantCode={}", host, companyName, tenantCode);

        Tenant tenant = null;

        try {
            // Priority 1: Try lookup by tenantCode first (exact match, highest priority for MSI deployments)
            if (tenantCode != null && !tenantCode.isBlank()) {
                var tenantCodeOpt = tenantRepository.findByTenantCode(tenantCode.trim().toUpperCase());
                if (tenantCodeOpt.isPresent()) {
                    tenant = tenantCodeOpt.get();
                    log.debug("Tenant found by tenantCode. tenantId={}, tenantName={}", tenant.getTenantID(), tenant.getTenantName());
                }
            }

            // Priority 2: Try lookup by host (domain or tenantName) - exact match then partial match
            if (tenant == null && host != null && !host.isBlank()) {
                // 1. Try exact domain match
                var domainOpt = tenantRepository.findByDomain(host);
                if (domainOpt.isPresent()) {
                    tenant = domainOpt.get();
                    log.debug("Tenant found by exact domain. tenantId={}, tenantName={}", tenant.getTenantID(), tenant.getTenantName());
                } else {
                    // 2. Try exact tenantName match
                    var nameOpt = tenantRepository.findByTenantName(host);
                    if (nameOpt.isPresent()) {
                        tenant = nameOpt.get();
                        log.debug("Tenant found by exact tenantName. tenantId={}, tenantName={}", tenant.getTenantID(), tenant.getTenantName());
                    } else {
                        // 3. Try partial domain match (contains) - returns first match
                        var domainContainsList = tenantRepository.findByDomainContaining(escapeLikePattern(host));
                        if (!domainContainsList.isEmpty()) {
                            tenant = domainContainsList.get(0);
                            log.debug("Tenant found by domain containing ({} matches). tenantId={}, tenantName={}",
                                    domainContainsList.size(), tenant.getTenantID(), tenant.getTenantName());
                        } else {
                            // 4. Try partial tenantName match (contains) - returns first match
                            var nameContainsList = tenantRepository.findByTenantNameContaining(escapeLikePattern(host));
                            if (!nameContainsList.isEmpty()) {
                                tenant = nameContainsList.get(0);
                                log.debug("Tenant found by tenantName containing ({} matches). tenantId={}, tenantName={}",
                                        nameContainsList.size(), tenant.getTenantID(), tenant.getTenantName());
                            }
                        }
                    }
                }
            }

            // If not found by host, try lookup by companyName (resolved from email domain)
            if (tenant == null && companyName != null && !companyName.isBlank()) {
                String resolvedDomain = resolveCompanyNameToDomain(companyName);
                log.debug("Resolved companyName={} to domain pattern={}", companyName, resolvedDomain);

                var emailList = tenantRepository.findByEmailDomainContaining(escapeLikePattern(resolvedDomain));
                if (!emailList.isEmpty()) {
                    tenant = emailList.get(0);
                    log.debug("Tenant found by email domain containing ({} matches). tenantId={}, tenantName={}",
                            emailList.size(), tenant.getTenantID(), tenant.getTenantName());
                }
            }
        } catch (Exception e) {
            log.error("Database error while fetching tenant config for host={}, companyName={}, tenantCode={}: {}",
                    host, companyName, tenantCode, e.getMessage(), e);
            throw new GlobalException("TENANT_LOOKUP_FAILED", "Failed to lookup tenant: " + e.getMessage());
        }

        if (tenant == null) {
            log.warn("Tenant not found for host={}, companyName={}, tenantCode={}", host, companyName, tenantCode);
            throw new ResourceNotFoundException("Tenant not found for host: " + host + ", companyName: " + companyName + ", tenantCode: " + tenantCode);
        }

        final Tenant finalTenant = tenant;
        AuthProviderConfig cfg = authProviderConfigRepository.findByTenant(finalTenant)
                .orElseThrow(() -> {
                    log.warn("Auth provider config missing for tenantId={}", finalTenant.getTenantID());
                    return new ResourceNotFoundException("Auth provider config missing");
                });

        log.info("Auth provider config loaded for tenantId={} issuer={}", tenant.getTenantID(), cfg.getIssuerUri());

        AuthDetailsDto dto = new AuthDetailsDto(
                tenant.getTenantID(),
                tenant.getTenantName(),
                tenant.getTenantName(),
                tenant.getTenantType(),
                cfg.getAuthServerUrl(),
                tenant.getRealmName(),
                cfg.getClientId(),
                cfg.getIssuerUri(),
                cfg.getJwkUri(),
                cfg.getTokenEndpoint(),
                tenant.getDomain(),
                tenant.getStatus()
        );

        log.debug("AuthDetailsDto constructed for tenantId={}", tenant.getTenantID());
        return dto;
    }


    private String resolveCompanyNameToDomain(String companyName) {
        String normalized = companyName.trim().toLowerCase();

        // Remove leading @ if present
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }

        // Remove domain extension if present (e.g., .com, .net, .org)
        if (normalized.contains(".")) {
            normalized = normalized.substring(0, normalized.indexOf('.'));
        }

        // Return pattern for LIKE search: "@companyname."
        return "@" + normalized + ".";
    }


    private void validateForFirstTime(CreateTenantRequest req, boolean isSystemLevel) {

        if (req.getTenantName() == null || req.getTenantName().isBlank()) {
            throw new KeycloakOperationException(
                    "ORGANIZATION_NAME_REQUIRED", 1000, "Organization name is required"
            );
        }

        // Keycloak realm names must be alphanumeric with hyphens/underscores only.
        // Spaces are NOT allowed because they are invalid in Keycloak realm names.
        if (!req.getTenantName().matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new KeycloakOperationException(
                    "INVALID_ORGANIZATION_NAME", 1000,
                    "Organization name must contain only letters, numbers, hyphens, and underscores (no spaces)"
            );
        }

        if (tenantRepository.existsByTenantName(req.getTenantName().toLowerCase())) {
            throw new KeycloakOperationException(
                    "ORGANIZATION_NAME_ALREADY_EXISTS", 1001,
                    "Organization name already exists"
            );
        }

        if (req.getDomain() == null || req.getDomain().isBlank()) {
            throw new KeycloakOperationException(
                    "INVALID_DOMAIN", 1007, "Domain is required"
            );
        }

        if (tenantRepository.existsByDomain(normalizeDomainForDB(req.getDomain()))) {
            throw new KeycloakOperationException(
                    "DOMAIN_ALREADY_EXISTS", 1002, "Domain already registered"
            );
        }

        if (req.getAdminEmail() == null || req.getAdminEmail().isBlank()) {
            throw new KeycloakOperationException(
                    "ADMIN_EMAIL_REQUIRED", 1007, "Admin email is required"
            );
        }

        if (userRepository.findByEmail(req.getAdminEmail()).isPresent()) {
            throw new KeycloakOperationException(
                    "ADMIN_EMAIL_ALREADY_EXISTS", 1004, "Admin email already exists"
            );
        }

        // Check email subdomain uniqueness for tenant email (if provided)
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String subdomain = extractEmailSubdomain(req.getEmail());
            boolean b = tenantRepository.existsByEmailSubdomain(subdomain);
            if (subdomain != null && b) {
                throw new KeycloakOperationException(
                        "EMAIL_SUBDOMAIN_ALREADY_EXISTS", 1008,
                        "Email subdomain already registered by another tenant"
                );
            }
        }

        // For system-level tenant creation (default tenant), allow realm to already exist
        // This handles cases where Keycloak realm exists but DB tenant record doesn't
        if (!isSystemLevel && kcUtil.realmExists(req.getTenantName())) {
            throw new KeycloakOperationException(
                    "ORGANIZATION_REALM_ALREADY_EXISTS", 1005,
                    "Realm already exists"
            );
        }
    }


    private Tenant buildTenantSkeleton(CreateTenantRequest req) {
        log.debug("buildTenantSkeleton: building tenant skeleton for tenantName={}", req.getTenantName());
        Tenant tenant = new Tenant();
        tenant.setTenantName(req.getTenantName().toLowerCase());
        tenant.setRealmName(req.getTenantName().toLowerCase());
        tenant.setDomain(normalizeDomainForDB(req.getDomain()));
        tenant.setRegion(req.getRegion());
        tenant.setPhoneNo(req.getPhoneNo());
        tenant.setTenantType(req.getTenantType());
        tenant.setSelfManaged(Boolean.TRUE.equals(req.getSelfManaged()));
        tenant.setSsoType(req.getSsoType());
        tenant.setIndustry(req.getIndustry());
        tenant.setTemporaryAddress(req.getTemporaryAddress());
        tenant.setPermanentAddress(req.getPermanentAddress());
        tenant.setBillingAddress(req.getBillingAddress());
        tenant.setBillingCycleType(req.getBillingCycleType());
        tenant.setCreatedAt(Instant.now());
        tenant.setEmail(req.getEmail());

        // tenantCode is generated lazily in finalizeTenant() after all Keycloak provisioning
        // succeeds, to avoid the synchronized bottleneck during the critical creation path.

        log.debug("buildTenantSkeleton: tenant skeleton created with realm={}, domain={}",
                tenant.getRealmName(), tenant.getDomain());
        return tenant;
    }

    private User buildAdminSkeleton(CreateTenantRequest req, Tenant tenant) {
        log.debug("buildAdminSkeleton: building admin skeleton for tenantName={}, adminUserName={}",
                req.getTenantName(), req.getAdminUserName());
        User admin = new User();
        admin.setFirstName(req.getAdminFirstName());
        admin.setLastName(req.getAdminLastName());
        admin.setUserName(req.getAdminEmail());
        if ("Master MSSP".equalsIgnoreCase(req.getTenantType())) {
            // legacy case: allow explicit username for master mssp
            admin.setUserName(req.getAdminUserName());
        }
        admin.setEmail(req.getAdminEmail());
        admin.setPhoneNo(req.getAdminPhoneNumber());
        admin.setTenant(tenant);
        admin.setDefaultUser(true);
        log.debug("buildAdminSkeleton: admin skeleton created with username={}, email={}", admin.getUserName(), admin.getEmail());
        return admin;
    }

    private Map<String, String> getSmtpConfig() {
        log.debug("getSmtpConfig: building SMTP configuration map from SmtpConfigService.");
        return smtpConfigService.toKeycloakSmtpMap(smtpConfigService.getDefaultSmtpConfig());
    }


    private Map<String, String> getSmtpConfigForTenant(String tenantId) {
        log.debug("getSmtpConfigForTenant: building SMTP configuration map for tenantId={}", tenantId);
        return smtpConfigService.toKeycloakSmtpMap(smtpConfigService.getSmtpConfigForTenant(tenantId));
    }

    // ============================================================================ HELPERS (domain + login URL)


    private String normalizeDomainForDB(String domain) {
        log.debug("normalizeDomainForDB: normalizing domain. rawDomain={}", domain);

        if (domain == null || domain.trim().isBlank()) {
            log.warn("Invalid domain provided while normalizing for DB. domain={}", domain);
            throw new KeycloakOperationException(ResponseCodes.INVALID_DOMAIN, 1007, "A valid domain must be provided.");
        }

        String d = domain.trim().toLowerCase();

        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);

        // Validate domain format: no double dots, no leading/trailing dots, max 253 chars
        if (d.contains("..") || d.startsWith(".") || d.endsWith(".") || d.length() > 253) {
            throw new KeycloakOperationException(ResponseCodes.INVALID_DOMAIN, 1007,
                    "Invalid domain format: " + domain);
        }

        // Validate domain characters: only alphanumeric, hyphens, dots allowed
        if (!d.matches("^[a-z0-9][a-z0-9.\\-]*[a-z0-9]$") && d.length() > 1) {
            throw new KeycloakOperationException(ResponseCodes.INVALID_DOMAIN, 1007,
                    "Domain contains invalid characters: " + domain);
        }

        // if domain already contains the extension, return as-is
        if (d.contains(extension.replaceFirst("^\\.", "")) || d.endsWith(extension)) {
            log.debug("Domain already contains extension. normalizedDomain={}", d);
            return d;
        }

        // take first segment before any dot and append extension
        String first = d.contains(".") ? d.split("\\.")[0] : d;
        String normalized = first + extension;
        log.debug("Domain normalized to {}", normalized);
        return normalized;
    }


    private String normalizeDomainForRedirect(String domain) {
        String redirect = "https://" + normalizeDomainForDB(domain) + "/*";
        log.debug("normalizeDomainForRedirect: rawDomain={}, redirectUri={}", domain, redirect);
        return redirect;
    }


    private String generateLoginUrl(CreateTenantRequest req) {
        // 1. Build the base redirect URI and the fundamental authorization URL
        String redirect = "https://" + normalizeDomainForDB(req.getDomain());
        StringBuilder loginUrlBuilder = new StringBuilder(baseUrl)
                .append("/realms/").append(req.getTenantName().toLowerCase())
                .append("/protocol/openid-connect/auth?client_id=").append(req.getTenantName().toLowerCase())
                .append("&redirect_uri=").append(URLEncoder.encode(redirect, StandardCharsets.UTF_8))
                .append("&response_type=code");

        // 2. Condition: If ssoType is AZURE, force the Hub-and-Spoke redirect
        // This tells the Tenant realm to instantly skip its local login and call 'master-hub'
        if ("AZURE".equalsIgnoreCase(req.getSsoType())) {
            loginUrlBuilder.append("&kc_idp_hint=master-hub");
        }

        String finalUrl = loginUrlBuilder.toString();
        log.debug("generateLoginUrl: tenantName={}, ssoType={}, loginUrl={}",
                req.getTenantName(), req.getSsoType(), finalUrl);

        return finalUrl;
    }


    private String buildSetPasswordUrl(Tenant tenant) {
        if ("AZURE".equalsIgnoreCase(tenant.getSsoType())) {
            return null;
        }
        return baseUrl
                + "/realms/" + tenant.getRealmName()
                + "/login-actions/reset-credentials"
                + "?client_id=" + tenant.getTenantName();
    }


    private TenantResponse buildResponse(Tenant tenant) {
        log.debug("buildResponse: building TenantResponse for tenantId={}, status={}",
                tenant.getTenantID(), tenant.getStatus());
        TenantResponse resp = new TenantResponse();
        resp.setTenantID(tenant.getTenantID());
        resp.setTenantName(tenant.getTenantName());
        resp.setRealmName(tenant.getRealmName());
        resp.setDomain(tenant.getDomain());
        resp.setRegion(tenant.getRegion());
        resp.setPhoneNo(tenant.getPhoneNo());
        resp.setStatus(tenant.getStatus());
        resp.setTenantType(tenant.getTenantType());
        resp.setIndustry(tenant.getIndustry());
        resp.setCreatedAt(tenant.getCreatedAt() != null ? tenant.getCreatedAt() : Instant.now());
        resp.setLoginUrl(tenant.getLoginUrl());

        if (tenant.getTenantID() != null) {
            String apiKey = tenantIdApiKeys.remove(tenant.getTenantID());
            if (apiKey != null) {
                resp.setApiKey(apiKey);
            }
            // No warning needed — API key is only present for APIKEY SSO type
        }

        // Fetch subscription info from database (non-blocking)
        try {
            subscriptionService.getActiveSubscription(tenant.getTenantID())
                    .ifPresent(resp::setSubscription);
        } catch (Exception e) {
            log.debug("Could not fetch subscription info for tenantId={}: {}",
                    tenant.getTenantID(), e.getMessage());
        }

        log.debug("buildResponse: response prepared for tenantId={}", tenant.getTenantID());
        return resp;
    }


    private TenantResponse buildResponseLightweight(Tenant tenant) {
        TenantResponse resp = new TenantResponse();
        resp.setTenantID(tenant.getTenantID());
        resp.setTenantName(tenant.getTenantName());
        resp.setRealmName(tenant.getRealmName());
        resp.setDomain(tenant.getDomain());
        resp.setRegion(tenant.getRegion());
        resp.setPhoneNo(tenant.getPhoneNo());
        resp.setStatus(tenant.getStatus());
        resp.setTenantType(tenant.getTenantType());
        resp.setIndustry(tenant.getIndustry());
        resp.setCreatedAt(tenant.getCreatedAt() != null ? tenant.getCreatedAt() : Instant.now());
        resp.setLoginUrl(tenant.getLoginUrl());
        // Subscription not fetched — caller should batch-load if needed
        return resp;
    }


    private void copyAddress(Address target, Address source) {

        if (source == null) return;

        if (target == null) {
            throw new IllegalStateException(
                    "Target address must already exist before update"
            );
        }

        if (source.getAddressLine1() != null)
            target.setAddressLine1(source.getAddressLine1());

        if (source.getAddressLine2() != null)
            target.setAddressLine2(source.getAddressLine2());

        if (source.getCity() != null)
            target.setCity(source.getCity());

        if (source.getState() != null)
            target.setState(source.getState());

        if (source.getCountry() != null)
            target.setCountry(source.getCountry());

        if (source.getPostalCode() != null)
            target.setPostalCode(source.getPostalCode());
    }



    @Transactional
    private void ensureRequesterIsParentOrSelf(HttpServletRequest request, Tenant target) {
        Tenant requester = jwtUtl.getTenantFromRequest(request);
        log.debug("ensureRequesterIsParentOrSelf: requesterTenantId={}, targetTenantId={}",
                requester != null ? requester.getTenantID() : "null", target.getTenantID());

        // allow if requester is the same tenant
        if (requester != null && requester.getTenantID().equals(target.getTenantID())) {
            log.debug("ensureRequesterIsParentOrSelf: requester is same as target - allowed");
            return;
        }

        // traverse upwards from target through parentTenantId chain to see if requester is an ancestor
        String currentParentId = target.getParentTenantId();
        while (currentParentId != null && !currentParentId.isBlank()) {
            if (requester != null && currentParentId.equals(requester.getTenantID())) {
                log.debug("ensureRequesterIsParentOrSelf: requester is an ancestor - allowed");
                return;
            }
            Optional<Tenant> parentOpt = tenantRepository.findByTenantID(currentParentId);
            if (parentOpt.isEmpty()) break;
            currentParentId = parentOpt.get().getParentTenantId();
        }

        log.warn("Access denied: requester tenantId={} is not an ancestor of tenantId={}",
                requester != null ? requester.getTenantID() : "null", target.getTenantID());
        throw new KeycloakOperationException(
                ResponseCodes.ACCESS_DENIED,
                1022,
                "Access denied to tenant."
        );
    }


    private void collectChildren(String tenantId, List<Tenant> result) {
        log.debug("collectChildren: collecting children for tenantId={}", tenantId);
        int maxDepth = 10;
        List<String> currentLevelIds = List.of(tenantId);

        for (int depth = 0; depth < maxDepth && !currentLevelIds.isEmpty(); depth++) {
            // Single batch query per level instead of per-node
            List<Tenant> children = tenantRepository.findByParentTenantIdIn(currentLevelIds);
            if (children.isEmpty()) break;

            result.addAll(children);
            currentLevelIds = children.stream()
                    .map(Tenant::getTenantID)
                    .collect(Collectors.toList());
        }

        if (!currentLevelIds.isEmpty() && !tenantRepository.findByParentTenantIdIn(currentLevelIds).isEmpty()) {
            log.warn("collectChildren: max depth {} reached for tenantId={}. Possible circular reference.", maxDepth, tenantId);
        }
    }

    // ============================================================================ SHORT-LIVED @Transactional DB HELPERS

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveTenantStatus(Tenant tenant, String status) {
        tenant.setStatus(status);
        try {
            int updated = tenantRepository.updateTenantStatus(
                    tenant.getTenantID(), status, tenant.getLoginUrl(), tenant.getTenantCode());
            if (updated == 0) {
                log.warn("saveTenantStatus: no rows updated for tenantId={} status={}", tenant.getTenantID(), status);
            }
        } catch (Exception e) {
            log.error("Failed to update tenant status to {} for tenantName={}: {}", status, tenant.getTenantName(), e.getMessage(), e);
            throw new GlobalException("TENANT_STATUS_UPDATE_FAILED", e.getMessage());
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveProvisionSteps(Tenant tenant, int steps) {
        tenant.setProvisionStepsCompleted(steps);
        int updated = tenantRepository.updateProvisionSteps(tenant.getTenantID(), steps);
        if (updated == 0) {
            log.warn("saveProvisionSteps: no rows updated for tenantId={} steps={}", tenant.getTenantID(), steps);
        }
    }


    @Transactional
    void saveUser(User user, String tenantName) {
        try {
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to save user for tenantName={}: {}", tenantName, e.getMessage(), e);
            throw new GlobalException("ADMIN_USER_UPDATE_FAILED", e.getMessage());
        }
    }

    /**
     * Persist a new Tenant skeleton in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistTenantSkeleton(Tenant tenant, String tenantName) {
        try {
            tenantRepository.save(tenant);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Duplicate tenant detected during persist for tenantName={}: {}", tenantName, e.getMessage());
            throw new KeycloakOperationException(
                    "ORGANIZATION_NAME_ALREADY_EXISTS", 1001,
                    "Tenant already exists (concurrent creation detected)"
            );
        } catch (Exception e) {
            log.error("Failed to persist new tenant skeleton for tenantName={}: {}", tenantName, e.getMessage(), e);
            throw new GlobalException("TENANT_PERSISTENCE_FAILED", e.getMessage());
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistAdminSkeleton(User admin, String tenantName) {
        try {
            userRepository.save(admin);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Duplicate admin user detected during persist for tenantName={}: {}", tenantName, e.getMessage());
            throw new KeycloakOperationException(
                    "ADMIN_EMAIL_ALREADY_EXISTS", 1004,
                    "Admin email already exists (concurrent creation detected)"
            );
        } catch (Exception e) {
            log.error("Failed to persist new admin skeleton for tenantName={}: {}", tenantName, e.getMessage(), e);
            throw new GlobalException("ADMIN_PERSISTENCE_FAILED", e.getMessage());
        }
    }

    private String generateUniqueTenantCode(String tenantName) {
        // Extract first 4 letters (uppercase), removing non-alphabetic characters
        String prefix = (tenantName != null ? tenantName : "TNNT")
                .replaceAll("[^a-zA-Z]", "")
                .toUpperCase();

        if (prefix.length() > 4) {
            prefix = prefix.substring(0, 4);
        } else if (prefix.isEmpty()) {
            prefix = "TNNT";
        }

        // Single-query approach: find the max existing code for this prefix,
        // then increment. DB unique constraint is the final safety net.
        String prefixWithDash = prefix + "-";
        int nextSequence = 1;

        Optional<String> maxCode = tenantRepository.findMaxTenantCodeByPrefix(prefixWithDash);
        if (maxCode.isPresent()) {
            try {
                String suffix = maxCode.get().substring(prefixWithDash.length());
                nextSequence = Integer.parseInt(suffix) + 1;
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                log.warn("generateUniqueTenantCode: could not parse max code '{}', starting from 1", maxCode.get());
            }
        }

        if (nextSequence > 999) {
            throw new GlobalException("TENANT_CODE_EXHAUSTED",
                    "All tenant codes exhausted for prefix: " + prefix);
        }

        String candidateCode = String.format("%s-%03d", prefix, nextSequence);
        log.debug("generateUniqueTenantCode: generated tenantCode={} for tenantName={}", candidateCode, tenantName);
        return candidateCode;
    }


    public Tenant getTenantByKeycloakRealmName(String realmName) {
        return tenantRepository.findByRealmName(realmName).orElse(null);
    }


    @Transactional
    public void syncSelfManagedRoles(String callerTenantId, String targetTenantId) {
        Tenant tenant = tenantRepository.findById(targetTenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + targetTenantId));

        if (!Boolean.TRUE.equals(tenant.getSelfManaged())) {
            throw new GlobalException("Tenant is not selfManaged — role sync is only applicable to selfManaged MSSP/Master MSSP tenants.");
        }

        List<User> tenantUsers = tenant.getUsers() != null ? tenant.getUsers() : new ArrayList<>();
        User admin = tenantUsers.stream()
                .filter(User::isDefaultUser)
                .findFirst()
                .orElseThrow(() -> new GlobalException("No default admin user found for tenant: " + tenant.getTenantName()));

        List<Roles> adminRoles = roleService.resolveAdminRolesForTenant(tenant, admin.getPkUserId());

        Groups adminGroup = groupService.getDefaultGroupForTenant(targetTenantId)
                .orElseThrow(() -> new GlobalException("No default admin group found for tenant: " + tenant.getTenantName()));

        adminRoles.forEach(role -> groupService.assignRoleToGroupIfMissing(adminGroup, role));

        log.info("✅ syncSelfManagedRoles: assigned {} roles to group '{}' for tenantId={}",
                adminRoles.size(), adminGroup.getName(), targetTenantId);
    }

    private void handleSelfManagedChange(Tenant tenant, Boolean newSelfManaged, HttpServletRequest request) {
        boolean current = Boolean.TRUE.equals(tenant.getSelfManaged());
        if (current == newSelfManaged) {
            log.info("selfManaged unchanged for tenantId={} ({}), skipping", tenant.getTenantID(), newSelfManaged);
            return;
        }

        String tenantType = Optional.ofNullable(tenant.getTenantType())
                .map(String::trim).map(String::toLowerCase).orElse("");

        if (!tenantType.equals("mssp") && !tenantType.equals("master mssp")
                && !tenantType.equals("master_mssp") && !tenantType.equals("mastermssp")) {
            throw new KeycloakOperationException(
                    "INVALID_TENANT_TYPE", 1042,
                    "selfManaged is only applicable to MSSP and Master MSSP tenant types"
            );
        }

        // Find default admin user
        User admin = Optional.ofNullable(tenant.getUsers())
                .orElse(List.of())
                .stream()
                .filter(User::isDefaultUser)
                .findFirst()
                .orElse(null);

        if (newSelfManaged) {
            // Enable: assign ENTERPRISE ADMIN role + ensure default group exists
            log.info("Enabling selfManaged for tenantId={}", tenant.getTenantID());
            tenant.setSelfManaged(true);

            if (admin != null) {
                roleService.createOrGetEnterpriseAdminRole(tenant.getTenantID(), admin.getPkUserId());
                log.info("✅ ENTERPRISE ADMIN role assigned to admin userId={}", admin.getPkUserId());
            }

            try {
                eventsGroupService.createDefaultGroupForTenant(tenant.getTenantID(), "SYSTEM");
                log.info("✅ Default events group ensured for selfManaged tenant={}", tenant.getTenantName());
            } catch (Exception e) {
                log.warn("⚠️ Failed to create default group for selfManaged tenant {}: {}",
                        tenant.getTenantName(), e.getMessage());
            }

        } else {
            // Disable: remove ENTERPRISE ADMIN role from admin user
            log.info("Disabling selfManaged for tenantId={}", tenant.getTenantID());
            tenant.setSelfManaged(false);

            if (admin != null) {
                try {
                    roleService.removeEnterpriseAdminRoleFromUser(tenant.getTenantID(), admin.getPkUserId());
                    log.info("✅ ENTERPRISE ADMIN role removed from admin userId={}", admin.getPkUserId());
                } catch (Exception e) {
                    log.warn("⚠️ Failed to remove ENTERPRISE ADMIN role for tenant {}: {}",
                            tenant.getTenantName(), e.getMessage());
                }
            }
        }
    }

    private void handleSsoTypeChange(Tenant tenant, String oldSsoType, String newSsoType, UpdateTenantRequest req) {
        String tenantId = tenant.getTenantID();
        String realmName = tenant.getRealmName();

        try {
            // =========================================
            // STEP 1: Teardown old SSO configuration
            // =========================================
            if (oldSsoType != null && !oldSsoType.equalsIgnoreCase(newSsoType)) {
                log.info("🗑️ Tearing down old SSO configuration: {}", oldSsoType);

                if ("AZURE".equalsIgnoreCase(oldSsoType)) {
                    teardownAzureSso(tenant);
                } else if ("APIKEY".equalsIgnoreCase(oldSsoType)) {
                    teardownApiKeySso(tenant);
                }
            }

            // =========================================
            // STEP 2: Update AuthProviderConfig
            // =========================================
            AuthProviderConfig authConfig = authProviderConfigRepository
                    .findByTenant_TenantID(tenantId)
                    .orElseGet(() -> {
                        log.info("Creating new AuthProviderConfig for tenant {}", tenantId);
                        AuthProviderConfig newConfig = new AuthProviderConfig();
                        newConfig.setTenant(tenant);
                        return newConfig;
                    });

            // Update SSO type
            authConfig.setSsoType(newSsoType);

            // Note: For Hub & Spoke Azure SSO, Azure credentials (clientId, clientSecret, tenantId)
            // are global and come from application properties, NOT per-tenant.
            // The AuthProviderConfig.clientId and .clientSecret refer to the tenant's Keycloak client,
            // which is managed automatically by setupHubAndSpokeSSO.

            authProviderConfigRepository.save(authConfig);
            log.info("✅ AuthProviderConfig updated: ssoType={}", newSsoType);

            // =========================================
            // STEP 3: Setup new SSO configuration
            // =========================================
            log.info("🔧 Setting up new SSO configuration: {}", newSsoType);

            if ("AZURE".equalsIgnoreCase(newSsoType)) {
                setupAzureSsoForExistingTenant(tenant, req);
            } else if ("APIKEY".equalsIgnoreCase(newSsoType)) {
                setupApiKeySsoForExistingTenant(tenant);
            }

            log.info("✅ SSO type change completed successfully: {} -> {}", oldSsoType, newSsoType);

        } catch (Exception e) {
            log.error("❌ SSO type change failed for tenant {}: {} -> {}",
                    tenantId, oldSsoType, newSsoType, e);
            throw new GlobalException("SSO_TYPE_CHANGE_FAILED",
                    "Failed to change SSO type: " + e.getMessage());
        }
    }


    private void teardownAzureSso(Tenant tenant) {
        String realmName = tenant.getRealmName();
        String tenantName = tenant.getTenantName();
        log.info("Tearing down Azure SSO for tenant realm: {}", realmName);

        try {
            // Step 1: Disable auto-redirect in tenant realm
            kcUtil.disableAutoRedirect(realmName);
            log.info("✅ Disabled auto-redirect in tenant realm: {}", realmName);

            // Step 2: Disable master-hub IdP in tenant realm
            // Note: We don't delete the master-hub IdP or broker client as they might be needed
            // for historical data or re-enabling. Just disable them.
            if (kcUtil.idpExists(realmName, "master-hub")) {
                kcUtil.disableIdp(realmName, "master-hub");
                log.info("✅ Disabled master-hub IdP in tenant realm: {}", realmName);
            }


            String orgAlias = tenantName.toLowerCase().replaceAll("\\s+", "-");
            try {
                kcUtil.unlinkIdpFromOrganization("master", orgAlias, masterAzureAlias);
                log.info("✅ Unlinked Azure IdP from organization '{}' in master realm", orgAlias);
            } catch (Exception e) {
                log.warn("⚠️ Failed to unlink Azure IdP from organization '{}': {}", orgAlias, e.getMessage());
                // Non-fatal - organization might not exist or IdP not linked
            }

            log.info("✅ Azure SSO teardown completed for tenant realm: {}", realmName);
        } catch (Exception e) {
            log.warn("⚠️ Azure SSO teardown had issues for realm {}: {}", realmName, e.getMessage());
            // Non-fatal - continue with SSO type change
        }
    }

    private void teardownApiKeySso(Tenant tenant) {
        String tenantId = tenant.getTenantID();
        log.info("Tearing down API Key SSO for tenant: {}", tenantId);

        try {
            // Disable existing API keys
            List<ExtensionApiKey> apiKeys = apiKeyRepository.findByTenantId(tenantId);
            for (ExtensionApiKey apiKey : apiKeys) {
                apiKey.setStatus("INACTIVE");
                apiKeyRepository.save(apiKey);
            }

            log.info("✅ Disabled {} API keys for tenant: {}", apiKeys.size(), tenantId);
        } catch (Exception e) {
            log.warn("⚠️ API Key SSO teardown had issues for tenant {}: {}", tenantId, e.getMessage());
            // Non-fatal - continue with SSO type change
        }
    }


    private void setupAzureSsoForExistingTenant(Tenant tenant, UpdateTenantRequest req) {
        String tenantId = tenant.getTenantID();
        String realmName = tenant.getRealmName();

        log.info("Setting up Azure SSO for existing tenant: {}", tenant.getTenantName());

        try {

            CreateTenantRequest setupReq = new CreateTenantRequest();
            setupReq.setTenantName(tenant.getTenantName());
            setupReq.setDomain(tenant.getDomain());
            setupReq.setSsoType("AZURE");

            // Run the Hub & Spoke SSO setup (idempotent)
            setupHubAndSpokeSSO(tenant, setupReq);

            log.info("✅ Azure SSO setup completed for tenant: {}", tenant.getTenantName());

        } catch (Exception e) {
            log.error("❌ Failed to setup Azure SSO for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new GlobalException("AZURE_SSO_SETUP_FAILED",
                    "Failed to setup Azure SSO: " + e.getMessage());
        }
    }


    private void setupApiKeySsoForExistingTenant(Tenant tenant) {
        String tenantId = tenant.getTenantID();

        log.info("Setting up API Key SSO for existing tenant: {}", tenant.getTenantName());

        try {
            // Create default API key group if not exists
            try {
                eventsGroupService.createDefaultGroupForTenant(tenantId, "SYSTEM");
                log.info("✅ Default API key group ensured for tenant: {}", tenant.getTenantName());
            } catch (Exception e) {
                log.warn("⚠️ Failed to create default API key group: {}", e.getMessage());
                // Non-fatal - group might already exist
            }

            // Generate new API key for tenant
            String apiKey = setupApiKeySSO(tenant);
            tenantIdApiKeys.put(tenantId, apiKey);

            log.info("✅ API Key SSO setup completed for tenant: {}", tenant.getTenantName());

        } catch (Exception e) {
            log.error("❌ Failed to setup API Key SSO for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new GlobalException("APIKEY_SSO_SETUP_FAILED",
                    "Failed to setup API Key SSO: " + e.getMessage());
        }
    }
}