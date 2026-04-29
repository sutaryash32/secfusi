package com.secufusion.tenant.service;

import com.fasterxml.jackson.databind.node.TextNode;
import com.secufusion.tenant.dto.BrowserPolicyWithAssignmentsDto;
import com.secufusion.tenant.dto.PolicyVersionResponse;
import com.secufusion.tenant.dto.UserEffectivePolicyResponse;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.entity.LandingPage;
import com.secufusion.tenant.repository.BrowserPolicyRepository;
import com.secufusion.tenant.repository.EventsGroupRepository;
import com.secufusion.tenant.repository.LandingPageRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import com.secufusion.tenant.util.JwtUtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BrowserPolicyService {

    private final BrowserPolicyRepository browserPolicyRepository;
    private final EntityManager entityManager;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final JwtUtl jwtUtl;
    private final EventsGroupRepository eventsGroupRepository;
    private final LandingPageRepository landingPageRepository;
    private final NetworkPolicyService networkPolicyService;
    private final ExtensionPolicyService extensionPolicyService;


    @Transactional
    public BrowserPolicy createdDefaultPolicyIfNotExists() {

        final String DEFAULT_POLICY_NAME = "Default Browser Policy";

        // 1️⃣ Global default already exists
        Optional<BrowserPolicy> existingGlobal =
                browserPolicyRepository.findTopByFkTenantIdIsNull();

        if (existingGlobal.isPresent()) {
            log.debug("Global default BrowserPolicy already exists");
            return existingGlobal.get();
        }

        // 2️⃣ Same-name global policy exists (extra safety)
        Optional<BrowserPolicy> sameNamePolicy =
                browserPolicyRepository.findTopByFkTenantIdIsNullAndNameIgnoreCase(DEFAULT_POLICY_NAME);

        if (sameNamePolicy.isPresent()) {
            log.debug("Global BrowserPolicy with same name already exists");
            return sameNamePolicy.get();
        }

        // 3️⃣ Create default policy
        log.info("No global default BrowserPolicy found. Creating one.");

        BrowserPolicy defaultPolicy = new BrowserPolicy();
        defaultPolicy.setName(DEFAULT_POLICY_NAME);
        defaultPolicy.setDescription(
                "If an authorized group is not assigned, this default policy will be applied."
        );
        defaultPolicy.setPolicyType(null);
        defaultPolicy.setActive(true);

        // DLP
        Dlp dlp = new Dlp();
        dlp.setWatermarking(new Watermarking());
        defaultPolicy.setDlp(dlp);

        // Compliance
        ComplianceRules complianceRules = new ComplianceRules();
        complianceRules.setGeolocation(false);
        complianceRules.setAntivirusCheck(false);
        complianceRules.setFirewallCheck(false);
        complianceRules.setDiskEncryptionCheck(false);
        defaultPolicy.setComplianceRules(complianceRules);

        // Homepage
        Homepage homepage = new Homepage();
        homepage.setTitle("");
        homepage.setUrl("");
        homepage.setDisableAddressBar(false);
        defaultPolicy.setHomepage(homepage);

        // LandingPage - null by default, user can enter URL or select existing landing page
        defaultPolicy.setLandingPageUrl(null);
        defaultPolicy.setLandingPageId(null);

        defaultPolicy.setUrlRestriction(new TextNode(""));
        defaultPolicy.setPolicyKey(UUID.randomUUID().toString());
        defaultPolicy.setVersion("0.1");

        try {
            return browserPolicyRepository.save(defaultPolicy);
        } catch (DataIntegrityViolationException ex) {
            // 4️⃣ Concurrency fallback
            log.warn("Default BrowserPolicy created concurrently. Fetching existing one.");
            return browserPolicyRepository.findTopByFkTenantIdIsNull()
                    .orElseThrow(() ->
                            new IllegalStateException("Default BrowserPolicy exists but cannot be fetched"));
        }
    }


    /* ==========================================================
       CREATE
       ========================================================== */
    public BrowserPolicy createPolicy(BrowserPolicy request) {

        request.setPolicyKey(UUID.randomUUID().toString());
        request.setVersion("0.1");
        request.setActive(true);


        BrowserPolicy saved = browserPolicyRepository.save(request);

        log.info(
                "✅ BrowserPolicy created successfully. policyId={}, policyKey={}, version={}",
                saved.getPkBrowserPolicyId(),
                saved.getPolicyKey(),
                saved.getVersion()
        );

        return saved;
    }

    /* ==========================================================
       READ
       ========================================================== */
    @Transactional(readOnly = true)
    public BrowserPolicy getPolicyById(String id) {

        log.debug("Fetching BrowserPolicy id={}", id);

        return browserPolicyRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("BrowserPolicy not found: " + id));
    }

    @Transactional
    public List<BrowserPolicy> getAllPolicies(String tenantId) {

        log.debug("Fetching ACTIVE BrowserPolicies for tenantId={}", tenantId);

        List<BrowserPolicy> policies =
                new ArrayList<>(
                        browserPolicyRepository
                                .findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(tenantId)
                );

        // Add default policy at top (tenant default preferred, else global)
        BrowserPolicy defaultPolicy = getDefaultPolicy(tenantId);

        boolean alreadyPresent = policies.stream()
                .anyMatch(p -> p.getPkBrowserPolicyId()
                        .equals(defaultPolicy.getPkBrowserPolicyId()));

        if (!alreadyPresent) {
            policies.add(0, defaultPolicy);
        }

        return policies;
    }


    /* ==========================================================
       UPDATE (VERSIONED)
       ========================================================== */
    public BrowserPolicy updatePolicy(
            String id,
            BrowserPolicy updatedPolicy,
            HttpServletRequest request) {

        BrowserPolicy current = getPolicyById(id);

        // Guard: prevent editing global default policy
        if (current.isGlobalDefault()) {
            throw new IllegalArgumentException("Cannot modify the global default Browser Policy");
        }

        // 1️⃣ deactivate old version — flush immediately so the DB sees is_active=FALSE
        //    before the new version INSERT hits the V19 unique index check.
        current.setActive(false);
        browserPolicyRepository.saveAndFlush(current);

        // 2️⃣ create new version row
        BrowserPolicy newVersion = new BrowserPolicy();

        newVersion.setPolicyKey(current.getPolicyKey());
        newVersion.setVersion(nextMinorVersion(current.getVersion()));
        newVersion.setActive(true);

        newVersion.setFkTenantId(current.getFkTenantId());
        newVersion.setTenantDefault(current.isTenantDefault());
        newVersion.setName(updatedPolicy.getName());
        newVersion.setDescription(updatedPolicy.getDescription());
        newVersion.setUrlRestriction(updatedPolicy.getUrlRestriction());
        newVersion.setPolicyType(updatedPolicy.getPolicyType());

        // Clone nested entities into new instances — never set ID to null on a managed entity
        // as Hibernate tracks managed objects and throws "identifier was altered" on PK mutation.
        Dlp srcDlp = updatedPolicy.getDlp() != null ? updatedPolicy.getDlp() : current.getDlp();
        if (srcDlp != null) {
            Watermarking srcWm = srcDlp.getWatermarking();
            Watermarking newWm = null;
            if (srcWm != null) {
                newWm = new Watermarking();
                newWm.setEnabled(srcWm.isEnabled());
                newWm.setImageUrl(srcWm.getImageUrl());
            }
            Dlp newDlp = new Dlp();
            newDlp.setDisableCopy(srcDlp.isDisableCopy());
            newDlp.setDisablePaste(srcDlp.isDisablePaste());
            newDlp.setDisableDownload(srcDlp.isDisableDownload());
            newDlp.setBlockPrinting(srcDlp.isBlockPrinting());
            newDlp.setClipboard(srcDlp.getClipboard());
            newDlp.setPiiDetection(srcDlp.getPiiDetection());
            newDlp.setFileOperations(srcDlp.getFileOperations());
            newDlp.setFormControls(srcDlp.getFormControls());
            newDlp.setCommunicationPlatforms(srcDlp.getCommunicationPlatforms());
            newDlp.setSecurityPolicies(srcDlp.getSecurityPolicies());
            newDlp.setBehaviorMonitoring(srcDlp.getBehaviorMonitoring());
            newDlp.setPolicyEnforcement(srcDlp.getPolicyEnforcement());
            newDlp.setWatermarking(newWm);
            newVersion.setDlp(newDlp);
        }

        ComplianceRules srcCr = updatedPolicy.getComplianceRules() != null
                ? updatedPolicy.getComplianceRules() : current.getComplianceRules();
        if (srcCr != null) {
            ComplianceRules newCr = new ComplianceRules();
            newCr.setAntivirusCheck(srcCr.isAntivirusCheck());
            newCr.setDiskEncryptionCheck(srcCr.isDiskEncryptionCheck());
            newCr.setFirewallCheck(srcCr.isFirewallCheck());
            newCr.setGeolocation(srcCr.isGeolocation());
            newVersion.setComplianceRules(newCr);
        }

        Homepage srcHp = updatedPolicy.getHomepage() != null
                ? updatedPolicy.getHomepage() : current.getHomepage();
        if (srcHp != null) {
            Homepage newHp = new Homepage();
            newHp.setTitle(srcHp.getTitle());
            newHp.setUrl(srcHp.getUrl());
            newHp.setDisableAddressBar(srcHp.isDisableAddressBar());
            newVersion.setHomepage(newHp);
        }

        // Treat blank string same as null: "" means "not provided by frontend", not "clear the value".
        // To explicitly clear a landing page, send JSON null (not "").
        newVersion.setLandingPageUrl(
                (updatedPolicy.getLandingPageUrl() != null && !updatedPolicy.getLandingPageUrl().isBlank())
                        ? updatedPolicy.getLandingPageUrl()
                        : current.getLandingPageUrl()
        );

        newVersion.setLandingPageId(
                (updatedPolicy.getLandingPageId() != null && !updatedPolicy.getLandingPageId().isBlank())
                        ? updatedPolicy.getLandingPageId()
                        : current.getLandingPageId()
        );

        // Clone ManagedExtension — never set ID to null on a managed entity.
        ManagedExtension srcMe = updatedPolicy.getManagedExtension() != null
                ? updatedPolicy.getManagedExtension() : current.getManagedExtension();
        if (srcMe != null) {
            ManagedExtension newMe = new ManagedExtension();
            newMe.setAction(srcMe.getAction());
            newMe.setEnforcementAction(srcMe.getEnforcementAction());
            newMe.setWarningMessage(srcMe.getWarningMessage());
            if (srcMe.getExtensions() != null) {
                List<ExtensionDetail> newDetails = srcMe.getExtensions().stream().map(e -> {
                    ExtensionDetail d = new ExtensionDetail();
                    d.setExtensionId(e.getExtensionId());
                    d.setExtensionName(e.getExtensionName());
                    d.setPublisher(e.getPublisher());
                    return d;
                }).toList();
                newMe.setExtensions(newDetails);
            }
            newVersion.setManagedExtension(newMe);
        }

        BrowserPolicy saved = browserPolicyRepository.save(newVersion);

        // Migrate PolicyAssignment references from old version to new version
        policyAssignmentRepository.updateBrowserPolicyReference(
                current.getPkBrowserPolicyId(),
                saved.getPkBrowserPolicyId()
        );

        return saved;
    }

    private String nextMinorVersion(String version) {

        if (version == null || !version.matches("\\d+\\.\\d+")) {
            log.warn("Invalid version '{}', resetting to 0.1", version);
            return "0.1";
        }

        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);

        if (minor < 9) {
            minor++;
        } else {
            major++;
            minor = 0;
        }
        return major + "." + minor;
    }

    /* ==========================================================
       DELETE
       ========================================================== */
    public void deletePolicy(String id) {

        BrowserPolicy existing = getPolicyById(id);

        // Guard: prevent deleting global default policy
        if (existing.isGlobalDefault()) {
            throw new IllegalArgumentException("Cannot delete the global default Browser Policy");
        }

        // Guard: prevent deleting tenant default policy
        if (existing.isTenantDefault()) {
            throw new IllegalArgumentException("Cannot delete the tenant default Browser Policy. You can edit it instead.");
        }

        browserPolicyRepository.delete(existing);

        log.info("✅ BrowserPolicy deleted id={}", id);
    }

    /* ==========================================================
       AUDIT / ENVERS (ADDED)
       ========================================================== */

    /**
     * Returns all audit revisions for a BrowserPolicy
     */
    @Transactional(readOnly = true)
    public List<Object[]> getPolicyRevisions(String policyId) {

        AuditReader reader = AuditReaderFactory.get(entityManager);

        return reader.createQuery()
                .forRevisionsOfEntity(BrowserPolicy.class, false, true)
                .add(AuditEntity.id().eq(policyId))
                .getResultList();
    }

    /**
     * Returns BrowserPolicy state at a specific revision
     */
    @Transactional(readOnly = true)
    public BrowserPolicy getPolicyAtRevision(String policyId, Number revision) {

        AuditReader reader = AuditReaderFactory.get(entityManager);
        return reader.find(BrowserPolicy.class, policyId, revision);
    }

    /* ==========================================================
       INTERNAL HELPERS
       ========================================================== */

    private void attachChildren(BrowserPolicy policy) {

        if (policy.getDlp() != null) {
            if (policy.getDlp().getPkDlpId() == null) {
                policy.getDlp().setPkDlpId(UUID.randomUUID().toString());
            }

            if (policy.getDlp().getWatermarking() != null &&
                    policy.getDlp().getWatermarking().getPkWatermarkingId() == null) {

                policy.getDlp().getWatermarking()
                        .setPkWatermarkingId(UUID.randomUUID().toString());
            }
        }

        if (policy.getComplianceRules() != null &&
                policy.getComplianceRules().getPkComplianceRulesId() == null) {

            policy.getComplianceRules()
                    .setPkComplianceRulesId(UUID.randomUUID().toString());
        }

        if (policy.getHomepage() != null &&
                policy.getHomepage().getPkHomepageId() == null) {

            policy.getHomepage()
                    .setPkHomepageId(UUID.randomUUID().toString());
        }
    }

    /**
     * Determines the correct policy for a user based on their JWT claims (Roles/Groups).
     * Logic:
     * 1. Check if any of the user's Roles/Groups are mapped to a specific policy.
     * 2. If yes, return that specific policy.
     * 3. If no, return the Tenant's Default Policy.
     */
    public BrowserPolicy getEffectivePolicy(String tenantId, List<String> roleNames, List<String> groupIds) {
        log.debug("Determining effective policy for Tenant: {} | Roles: {} | Groups: {}",
                tenantId, roleNames != null ? roleNames.size() : 0, groupIds != null ? groupIds.size() : 0);

        try {
            List<String> safeRoles = (roleNames == null) ? Collections.emptyList() : roleNames;
            List<String> safeGroups = (groupIds == null) ? Collections.emptyList() : groupIds;

            if (safeRoles.isEmpty() && safeGroups.isEmpty()) {
                log.warn("No roles or groups provided for tenant {}. Skipping DB lookup.", tenantId);
                return getDefaultPolicy(tenantId);
            }

            List<PolicyAssignment> matches = policyAssignmentRepository.findEffectiveAssignments(tenantId, safeGroups, safeRoles);

            if (matches != null && !matches.isEmpty()) {
                PolicyAssignment match = matches.stream()
                        .filter(Objects::nonNull)
                        .filter(a -> a.getBrowserPolicy() != null)
                        .findFirst()
                        .orElse(null);

                if (match != null) {
                    log.info("✅ Found Policy: '{}' via Assignment ID: {} (Type: {})",
                            match.getBrowserPolicy().getName(), match.getId(), match.getAssignmentType());
                    return match.getBrowserPolicy();
                }
            }

        } catch (Exception e) {
            log.error("Unexpected error determining effective policy for tenant {}. Reason: {}",
                    tenantId, e.getMessage(), e);
        }

        log.info("Falling back to default policy for tenant: {}", tenantId);
        return getDefaultPolicy(tenantId);
    }

    public BrowserPolicy resolvePolicyForCurrentUser(HttpServletRequest request) {
        log.debug("[POLICY CHECK] Starting verification for incoming request...");

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        if (tenant == null || tenant.getTenantID() == null) {
            throw new SecurityException("Tenant context missing in token");
        }
        String tenantId = tenant.getTenantID();

        List<String> roles = jwtUtl.getClaimAsStringList(request, "roles");
        List<String> jwtGroups = jwtUtl.getClaimAsStringList(request, "groups");

        if (roles == null) roles = new ArrayList<>();
        if (jwtGroups == null) jwtGroups = new ArrayList<>();

        // Resolve JWT group identifiers (Azure OIDs or EventsGroup IDs) to EventsGroup PKs
        // This handles both AZURE_GROUP (azureGroupId match) and APIKEY_GROUP (pkEventsGroupId match)
        final List<String> eventsGroupIds = new ArrayList<>();
        if (!jwtGroups.isEmpty()) {
            List<EventsGroup> matchedGroups = eventsGroupRepository
                    .findByTenantIdAndIdentifiers(tenantId, jwtGroups);
            eventsGroupIds.addAll(matchedGroups.stream()
                    .map(EventsGroup::getPkEventsGroupId)
                    .collect(Collectors.toList()));
            log.info("Resolved {} JWT groups to {} EventsGroup IDs for tenant {}",
                    jwtGroups.size(), eventsGroupIds.size(), tenantId);
        }

        // Machine-token fallback: client_credentials JWTs (APIKEY SSO users) carry no groups/roles.
        // Use the tenant's default APIKEY_GROUP directly — no schema change needed.
        if (eventsGroupIds.isEmpty() && roles.isEmpty()) {
            eventsGroupRepository.findByTenantIdAndIsDefault(tenantId, true)
                    .ifPresent(g -> {
                        eventsGroupIds.add(g.getPkEventsGroupId());
                        log.info("Machine-token fallback: using default group {} for tenant {}", g.getPkEventsGroupId(), tenantId);
                    });
        }

        log.info("User Context: Tenant={} | Roles={} | EventsGroups={}", tenantId, roles, eventsGroupIds);

        return getEffectivePolicy(tenantId, roles, eventsGroupIds);
    }

    public UserEffectivePolicyResponse resolveAllPoliciesForCurrentUser(HttpServletRequest request) {
        log.debug("[TENANT POLICY] Resolving all effective policies for current user");

        // 1. BrowserPolicy — resolvePolicyForCurrentUser always returns non-null (falls back to default)
        BrowserPolicy browserPolicy = resolvePolicyForCurrentUser(request);

        // Extract tenant + roles + eventsGroupIds from request (same logic as resolvePolicyForCurrentUser)
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();

        List<String> roles = jwtUtl.getClaimAsStringList(request, "roles");
        List<String> jwtGroups = jwtUtl.getClaimAsStringList(request, "groups");
        if (roles == null) roles = new ArrayList<>();
        if (jwtGroups == null) jwtGroups = new ArrayList<>();

        final List<String> eventsGroupIds = new ArrayList<>();
        if (!jwtGroups.isEmpty()) {
            List<EventsGroup> matchedGroups = eventsGroupRepository
                    .findByTenantIdAndIdentifiers(tenantId, jwtGroups);
            eventsGroupIds.addAll(matchedGroups.stream()
                    .map(EventsGroup::getPkEventsGroupId)
                    .collect(Collectors.toList()));
        }

        // Machine-token fallback: same as resolvePolicyForCurrentUser
        if (eventsGroupIds.isEmpty() && roles.isEmpty()) {
            eventsGroupRepository.findByTenantIdAndIsDefault(tenantId, true)
                    .ifPresent(g -> eventsGroupIds.add(g.getPkEventsGroupId()));
        }

        // 2. NetworkPolicy — resolve via group/role assignments, fallback to default
        NetworkPolicy networkPolicy = networkPolicyService.getEffectivePolicy(tenantId, roles, eventsGroupIds);

        // 3. ExtensionPolicy — getEffectivePolicy always returns non-null (falls back to default with relations)
        ExtensionPolicy extensionPolicy = extensionPolicyService.getEffectivePolicy(tenantId, roles, eventsGroupIds);

        // Resolve LandingPage by ID — check browserPolicy first, then extensionPolicy.
        // The landingPageUrl (direct URL string) is already embedded in the respective policy object.
        LandingPage landingPage = null;
        String landingPageId = (browserPolicy.getLandingPageId() != null && !browserPolicy.getLandingPageId().isBlank())
                ? browserPolicy.getLandingPageId()
                : (extensionPolicy.getLandingPageId() != null && !extensionPolicy.getLandingPageId().isBlank())
                        ? extensionPolicy.getLandingPageId()
                        : null;
        if (landingPageId != null) {
            try {
                landingPage = landingPageRepository.findByIdWithShortcuts(landingPageId).orElse(null);
                if (landingPage == null) {
                    log.warn("Policy references landingPageId={} but no LandingPage found for tenant={}",
                            landingPageId, tenantId);
                }
            } catch (Exception e) {
                log.warn("Failed to resolve landingPageId={} for tenant={}: {}", landingPageId, tenantId, e.getMessage());
            }
        }

        log.info("Resolved all policies for tenant={} browserPolicy={} networkPolicy={} extensionPolicy={} landingPageId={}",
                tenantId,
                browserPolicy.getPkBrowserPolicyId(),
                networkPolicy.getPkNetworkPolicyId(),
                extensionPolicy.getPkExtensionPolicyId(),
                landingPageId);

        return UserEffectivePolicyResponse.builder()
                .browserPolicy(browserPolicy)
                .networkPolicy(networkPolicy)
                .extensionPolicy(extensionPolicy)
                .landingPage(landingPage)
                .build();
    }

    /**
     * Lightweight version check — returns only policy IDs and versions.
     * The extension polls this endpoint and only fetches full policies when versions change.
     */
    @Transactional(readOnly = true)
    public PolicyVersionResponse getPolicyVersionsForCurrentUser(HttpServletRequest request) {
        log.debug("[POLICY VERSION] Checking policy versions for current user");

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        if (tenant == null || tenant.getTenantID() == null) {
            throw new SecurityException("Tenant context missing in token");
        }
        String tenantId = tenant.getTenantID();

        List<String> roles = jwtUtl.getClaimAsStringList(request, "roles");
        List<String> jwtGroups = jwtUtl.getClaimAsStringList(request, "groups");
        if (roles == null) roles = new ArrayList<>();
        if (jwtGroups == null) jwtGroups = new ArrayList<>();

        final List<String> eventsGroupIds = new ArrayList<>();
        if (!jwtGroups.isEmpty()) {
            List<EventsGroup> matchedGroups = eventsGroupRepository
                    .findByTenantIdAndIdentifiers(tenantId, jwtGroups);
            eventsGroupIds.addAll(matchedGroups.stream()
                    .map(EventsGroup::getPkEventsGroupId)
                    .collect(Collectors.toList()));
        }

        // Machine-token fallback: client_credentials JWTs carry no groups/roles.
        if (eventsGroupIds.isEmpty() && roles.isEmpty()) {
            eventsGroupRepository.findByTenantIdAndIsDefault(tenantId, true)
                    .ifPresent(g -> {
                        eventsGroupIds.add(g.getPkEventsGroupId());
                        log.info("Machine-token fallback: using default group {} for tenant {}", g.getPkEventsGroupId(), tenantId);
                    });
        }

        BrowserPolicy bp = getEffectivePolicy(tenantId, roles, eventsGroupIds);
        NetworkPolicy np = networkPolicyService.getEffectivePolicy(tenantId, roles, eventsGroupIds);
        ExtensionPolicy ep = extensionPolicyService.getEffectivePolicy(tenantId, roles, eventsGroupIds);

        return PolicyVersionResponse.builder()
                .browserPolicy(PolicyVersionResponse.PolicyVersionInfo.builder()
                        .policyId(bp.getPkBrowserPolicyId())
                        .policyKey(bp.getPolicyKey())
                        .version(bp.getVersion())
                        .globalDefault(bp.isGlobalDefault())
                        .build())
                .networkPolicy(PolicyVersionResponse.PolicyVersionInfo.builder()
                        .policyId(np.getPkNetworkPolicyId())
                        .policyKey(np.getPolicyKey())
                        .version(np.getVersion())
                        .globalDefault(np.isGlobalDefault())
                        .build())
                .extensionPolicy(PolicyVersionResponse.PolicyVersionInfo.builder()
                        .policyId(ep.getPkExtensionPolicyId())
                        .policyKey(ep.getPolicyKey())
                        .version(ep.getVersion())
                        .globalDefault(ep.isGlobalDefault())
                        .build())
                .build();
    }

    /**
     * Returns the default BrowserPolicy for a tenant.
     * Priority: tenant-specific default → global default.
     */
    public BrowserPolicy getDefaultPolicy(String tenantId) {
        log.debug("Fetching default BrowserPolicy for tenantId={}", tenantId);

        if (tenantId != null) {
            // 1. Check for existing tenant default
            Optional<BrowserPolicy> tenantDefault =
                    browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId);
            if (tenantDefault.isPresent()) {
                return tenantDefault.get();
            }

            // 2. Lazy-create tenant default (runs only once per tenant, then cached in DB)
            log.info("No tenant default BrowserPolicy found for tenantId={}. Creating one.", tenantId);
            return createTenantDefaultPolicy(tenantId);
        }

        // 3. No tenant context — global default
        return createdDefaultPolicyIfNotExists();
    }

    /**
     * Creates a tenant-specific default BrowserPolicy by cloning the global default.
     * Idempotent — returns existing tenant default if already present.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public BrowserPolicy createTenantDefaultPolicy(String tenantId) {
        log.info("Ensuring tenant default BrowserPolicy for tenantId={}", tenantId);

        // Already exists?
        Optional<BrowserPolicy> existing =
                browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId);
        if (existing.isPresent()) {
            log.debug("Tenant default BrowserPolicy already exists for tenantId={}", tenantId);
            return existing.get();
        }

        // Clone from global default
        BrowserPolicy global = createdDefaultPolicyIfNotExists();

        BrowserPolicy tenantDefault = new BrowserPolicy();
        tenantDefault.setFkTenantId(tenantId);
        tenantDefault.setTenantDefault(true);
        tenantDefault.setName(global.getName());
        tenantDefault.setDescription(global.getDescription());
        tenantDefault.setPolicyType(global.getPolicyType());
        tenantDefault.setActive(true);
        tenantDefault.setPolicyKey(UUID.randomUUID().toString());
        tenantDefault.setVersion("0.1");
        tenantDefault.setUrlRestriction(global.getUrlRestriction());
        tenantDefault.setLandingPageUrl(global.getLandingPageUrl());
        tenantDefault.setLandingPageId(global.getLandingPageId());

        // Clone nested entities (new instances to avoid shared references)
        Dlp dlp = new Dlp();
        dlp.setWatermarking(new Watermarking());
        tenantDefault.setDlp(dlp);

        ComplianceRules cr = new ComplianceRules();
        cr.setGeolocation(false);
        cr.setAntivirusCheck(false);
        cr.setFirewallCheck(false);
        cr.setDiskEncryptionCheck(false);
        tenantDefault.setComplianceRules(cr);

        Homepage hp = new Homepage();
        hp.setTitle("");
        hp.setUrl("");
        hp.setDisableAddressBar(false);
        tenantDefault.setHomepage(hp);

        BrowserPolicy saved;
        try {
            saved = browserPolicyRepository.save(tenantDefault);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Tenant default BrowserPolicy created concurrently for tenantId={}. Fetching existing.", tenantId);
            return browserPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Tenant default BrowserPolicy exists but cannot be fetched for tenantId=" + tenantId));
        }
        log.info("Tenant default BrowserPolicy created for tenantId={}, policyId={}",
                tenantId, saved.getPkBrowserPolicyId());
        return saved;
    }

    /* ==========================================================
       POLICY WITH ASSIGNMENT STATUS
       ========================================================== */

    /**
     * Get a single policy by ID with its assignment status.
     *
     * @param id Policy ID
     * @return BrowserPolicyWithAssignmentsDto containing policy and assignments
     */
    @Transactional(readOnly = true)
    public BrowserPolicyWithAssignmentsDto getPolicyByIdWithAssignments(String id) {
        log.debug("Fetching BrowserPolicy with assignments, id={}", id);

        BrowserPolicy policy = getPolicyById(id);
        List<PolicyAssignment> assignments = policyAssignmentRepository
                .findByBrowserPolicy_PkBrowserPolicyId(id);

        return BrowserPolicyWithAssignmentsDto.fromEntity(policy, assignments);
    }

    /**
     * Get all policies for a tenant with their assignment status.
     *
     * @param tenantId Tenant ID
     * @return List of BrowserPolicyWithAssignmentsDto
     */
    @Transactional(readOnly = true)
    public List<BrowserPolicyWithAssignmentsDto> getAllPoliciesWithAssignments(String tenantId) {
        log.debug("Fetching all ACTIVE BrowserPolicies with assignments for tenantId={}", tenantId);

        List<BrowserPolicy> policies = getAllPolicies(tenantId);

        // Fetch all assignments for this tenant in one query for efficiency
        List<PolicyAssignment> allAssignments = policyAssignmentRepository.findByTenantId(tenantId);

        // Group assignments by policy ID (filter out assignments without browserPolicy to avoid NPE)
        Map<String, List<PolicyAssignment>> assignmentsByPolicyId = allAssignments.stream()
                .filter(a -> a.getBrowserPolicy() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getBrowserPolicy().getPkBrowserPolicyId()
                ));

        // Map each policy to DTO with its assignments
        return policies.stream()
                .map(policy -> {
                    List<PolicyAssignment> policyAssignments = assignmentsByPolicyId
                            .getOrDefault(policy.getPkBrowserPolicyId(), Collections.emptyList());
                    return BrowserPolicyWithAssignmentsDto.fromEntity(policy, policyAssignments);
                })
                .collect(Collectors.toList());
    }

    /**
     * Resolve effective policy for current user with assignment details.
     *
     * @param request HttpServletRequest containing JWT token
     * @return BrowserPolicyWithAssignmentsDto for the effective policy
     */
    @Transactional(readOnly = true)
    public BrowserPolicyWithAssignmentsDto resolveEffectivePolicyWithAssignments(HttpServletRequest request) {
        log.debug("Resolving effective policy with assignments for current user");

        BrowserPolicy effectivePolicy = resolvePolicyForCurrentUser(request);

        if (effectivePolicy == null) {
            return null;
        }

        List<PolicyAssignment> assignments = policyAssignmentRepository
                .findByBrowserPolicy_PkBrowserPolicyId(effectivePolicy.getPkBrowserPolicyId());

        return BrowserPolicyWithAssignmentsDto.fromEntity(effectivePolicy, assignments);
    }
}