package com.secufusion.tenant.service;

import com.secufusion.tenant.config.ExtensionPolicyDefaults;
import com.secufusion.tenant.dto.ExtensionDetailDto;
import com.secufusion.tenant.dto.ManagedExtensionDto;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.EventsGroupRepository;
import com.secufusion.tenant.repository.ExtensionPolicyRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import com.secufusion.tenant.repository.UrlFilterRepository;
import com.secufusion.tenant.util.JwtUtl;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ExtensionPolicyService {

    private final ExtensionPolicyRepository extensionPolicyRepository;
    private final ExtensionPolicyDefaults extensionPolicyDefaults;
    private final JwtUtl jwtUtl;
    private final EntityManager entityManager;
    private final PolicyAssignmentRepository policyAssignmentRepository;
    private final EventsGroupRepository eventsGroupRepository;

    private final UrlFilterRepository urlFilterRepository;

    /* ==========================================================
       DEFAULT POLICY
       ========================================================== */

    public ExtensionPolicy createDefaultPolicyIfNotExists() {

        Optional<ExtensionPolicy> existing =
                extensionPolicyRepository.findTopByFkTenantIdIsNull();

        if (existing.isPresent()) {
            return existing.get();
        }

        ExtensionPolicy policy = new ExtensionPolicy();
        policy.setName("Default Extension Policy");
        policy.setDescription("Applied when no extension policy is assigned");
        policy.setPolicyKey(UUID.randomUUID().toString());
        policy.setVersion("0.1");
        policy.setIsActive(true);

        // Initialize default ManagedExtension
        ManagedExtension me = new ManagedExtension();
        me.setAction(ExtensionAction.ALLOW_ALL);
        me.setEnforcementAction(extensionPolicyDefaults.getEnforcementAction());
        me.setWarningMessage(extensionPolicyDefaults.getWarningMessage());
        policy.setManagedExtension(me);

        return extensionPolicyRepository.save(policy);
    }

    /* ==========================================================
       CREATE
       ========================================================== */

    public ExtensionPolicy createPolicy(
            ExtensionPolicy dto,
            HttpServletRequest request
    ) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);

//        ExtensionPolicy policy = new ExtensionPolicy();
        dto.setFkTenantId(tenant.getTenantID());
//        policy.setName(dto.getName());
//        policy.setDescription(dto.getDescription());
//        policy.setLandingPageUrl(dto.getLandingPageUrl());
        dto.setPolicyKey(UUID.randomUUID().toString());
        dto.setVersion("0.1");
        dto.setIsActive(true);

        // Link URL filters to policy before save so they persist in the same transaction
        if (dto.getUrlFilters() != null) {
            dto.getUrlFilters().forEach(urlFilter -> urlFilter.setExtensionPolicy(dto));
        }

        ExtensionPolicy saved;
        try {
            saved = extensionPolicyRepository.save(dto);
        } catch (Exception e){
            log.error("Error saving ExtensionPolicy: {}", e.getMessage());
            throw e;
        }

        // Re-fetch with relations to avoid LazyInitializationException
        ExtensionPolicy savedWithRelations = extensionPolicyRepository
                .findByIdWithRelations(saved.getPkExtensionPolicyId())
                .orElse(saved);

        return savedWithRelations;
    }

    /* ==========================================================
       READ
       ========================================================== */

    public ExtensionPolicy getPolicyById(String id) {
        return extensionPolicyRepository.findByIdWithRelations(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("ExtensionPolicy not found: " + id));
    }

    /**
     * Retrieve a policy by ID with tenant ownership validation.
     * Global default policies (fkTenantId=null) are accessible to all tenants.
     */
    @Transactional(readOnly = true)
    public ExtensionPolicy getPolicyById(String id, String tenantId) {
        ExtensionPolicy policy = getPolicyById(id);
        if (policy.getFkTenantId() != null && !tenantId.equals(policy.getFkTenantId())) {
            log.warn("getPolicyById() policy {} does not belong to tenant {}", id, tenantId);
            throw new IllegalArgumentException("ExtensionPolicy not found: " + id);
        }
        return policy;
    }

    public List<ExtensionPolicy> getAllPolicies(String tenantId) {
        List<ExtensionPolicy> policies =
                new ArrayList<>(extensionPolicyRepository
                        .findAllByFkTenantIdAndIsActiveTrueOrderByCreatedAtDesc(tenantId));

        // Add default policy (tenant default preferred, else global)
        ExtensionPolicy defaultPolicy = getDefaultPolicy(tenantId);
        boolean alreadyPresent = policies.stream()
                .anyMatch(p -> p.getPkExtensionPolicyId().equals(defaultPolicy.getPkExtensionPolicyId()));
        if (!alreadyPresent) {
            policies.add(0, defaultPolicy);
        }

        return policies;
    }

    /* ==========================================================
       UPDATE (VERSIONED)
       ========================================================== */

    public ExtensionPolicy updatePolicy(
            String id,
            ExtensionPolicy updatedPolicy,
            HttpServletRequest request
    ) {
        ExtensionPolicy current = getPolicyById(id);

        // Guard: prevent editing global default policy
        if (current.isGlobalDefault()) {
            throw new IllegalArgumentException("Cannot modify the global default Extension Policy");
        }

        // 1. deactivate old version — flush immediately so the DB sees is_active=FALSE
        //    before the new version INSERT hits the V19 unique index check.
        current.setIsActive(false);
        extensionPolicyRepository.saveAndFlush(current);

        // 2. create new version row
        ExtensionPolicy newVersion = new ExtensionPolicy();

        newVersion.setPolicyKey(current.getPolicyKey());
        newVersion.setVersion(nextMinorVersion(current.getVersion()));
        newVersion.setIsActive(true);
        newVersion.setFkTenantId(current.getFkTenantId());
        newVersion.setTenantDefault(current.isTenantDefault());
        newVersion.setName(updatedPolicy.getName());
        newVersion.setDescription(updatedPolicy.getDescription());
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

        // Clone nested entities into new instances — never set ID to null on a managed entity.
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

        ExtensionPolicy saved = extensionPolicyRepository.save(newVersion);

        // Update PolicyAssignment mappings to point to the latest policy version
        policyAssignmentRepository.updateExtensionPolicyReference(
                current.getPkExtensionPolicyId(),
                saved.getPkExtensionPolicyId()
        );

        // URL filters: use from request if provided, otherwise carry forward from current.
        // Always create new UrlFilter instances — never mutate PKs of Hibernate-managed entities.
        List<UrlFilter> srcFilters = (updatedPolicy.getUrlFilters() != null && !updatedPolicy.getUrlFilters().isEmpty())
                ? updatedPolicy.getUrlFilters()
                : current.getUrlFilters();
        if (srcFilters != null && !srcFilters.isEmpty()) {
            List<UrlFilter> newFilters = new ArrayList<>();
            for (UrlFilter src : srcFilters) {
                UrlFilter uf = new UrlFilter();
                uf.setFilterType(src.getFilterType());
                uf.setPatternType(src.getPatternType());
                uf.setPattern(src.getPattern());
                uf.setDescription(src.getDescription());
                uf.setExtensionPolicy(saved);
                newFilters.add(uf);
            }
            urlFilterRepository.saveAll(newFilters);
        }

        // Re-fetch with relations to avoid LazyInitializationException
        ExtensionPolicy savedWithRelations = extensionPolicyRepository
                .findByIdWithRelations(saved.getPkExtensionPolicyId())
                .orElse(saved);

        return savedWithRelations;
    }

    /* ==========================================================
       DELETE
       ========================================================== */

    public void deletePolicy(String id) {
        ExtensionPolicy policy = getPolicyById(id);

        // Guard: prevent deleting global default policy
        if (policy.isGlobalDefault()) {
            throw new IllegalArgumentException("Cannot delete the global default Extension Policy");
        }

        // Guard: prevent deleting tenant default policy
        if (policy.isTenantDefault()) {
            throw new IllegalArgumentException("Cannot delete the tenant default Extension Policy. You can edit it instead.");
        }

        extensionPolicyRepository.delete(policy);
    }

    /* ==========================================================
       HELPERS
       ========================================================== */

    private ManagedExtension buildManagedExtension(ManagedExtensionDto dto) {

        if (dto == null) return null;

        ManagedExtension me = new ManagedExtension();

        me.setAction(ExtensionAction.valueOf(dto.getAction()));
        me.setEnforcementAction(dto.getEnforcementAction());
        me.setWarningMessage(dto.getWarningMessage());

        if (dto.getExtensions() != null) {
            List<ExtensionDetail> details =
                    dto.getExtensions().stream()
                            .map(this::mapDetail)
                            .toList();

            me.setExtensions(details);
        }

        return me;
    }

    private ExtensionDetail mapDetail(ExtensionDetailDto dto) {
        ExtensionDetail d = new ExtensionDetail();
        d.setExtensionId(dto.getExtensionId());
        d.setExtensionName(dto.getExtensionName());
        d.setPublisher(dto.getPublisher());
        return d;
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

    public ExtensionPolicy resolvePolicyForCurrentUser(HttpServletRequest request) {
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

        // Resolve JWT group identifiers (Azure OIDs or EventsGroup IDs) to EventsGroup PKs.
        // PolicyAssignment.azureResourceId stores EventsGroup PKs, NOT raw Azure OIDs.
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

        // Machine-token fallback: client_credentials JWTs carry no groups/roles.
        // Use the tenant's default APIKEY_GROUP directly.
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

    public ExtensionPolicy getEffectivePolicy(String tenantId, List<String> roleNames, List<String> groupIds) {

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
                        .filter(a -> a.getExtensionPolicy() != null)
                        .findFirst()
                        .orElse(null);

                if (match != null) {
                    log.info("Found ExtensionPolicy: '{}' via Assignment ID: {} (Type: {})",
                            match.getExtensionPolicy().getName(), match.getId(), match.getAssignmentType());
                    // Re-fetch with relations to avoid LazyInitializationException
                    return extensionPolicyRepository
                            .findByIdWithRelations(match.getExtensionPolicy().getPkExtensionPolicyId())
                            .orElse(match.getExtensionPolicy());
                }
            }

        } catch (Exception e) {
            log.error("Unexpected error determining effective policy for tenant {}. Reason: {}",
                    tenantId, e.getMessage(), e);
        }

        log.info("Falling back to default ExtensionPolicy for tenant: {}", tenantId);
        return getDefaultPolicy(tenantId);
    }

    /**
     * Returns the default ExtensionPolicy for a tenant.
     * Priority: tenant-specific default (lazy-created) → global default.
     */
    private ExtensionPolicy getDefaultPolicy(String tenantId) {
        if (tenantId != null) {
            // 1. Check for existing tenant default
            Optional<ExtensionPolicy> tenantDefault =
                    extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId);
            if (tenantDefault.isPresent()) {
                return extensionPolicyRepository
                        .findByIdWithRelations(tenantDefault.get().getPkExtensionPolicyId())
                        .orElse(tenantDefault.get());
            }

            // 2. Lazy-create tenant default (runs only once per tenant)
            log.info("No tenant default ExtensionPolicy found for tenantId={}. Creating one.", tenantId);
            ExtensionPolicy created = createTenantDefaultPolicy(tenantId);
            return extensionPolicyRepository
                    .findByIdWithRelations(created.getPkExtensionPolicyId())
                    .orElse(created);
        }

        // 3. No tenant context — global default
        ExtensionPolicy defaultPolicy = createDefaultPolicyIfNotExists();
        return extensionPolicyRepository
                .findByIdWithRelations(defaultPolicy.getPkExtensionPolicyId())
                .orElse(defaultPolicy);
    }

    /**
     * Creates a tenant-specific default ExtensionPolicy by cloning the global default.
     * Idempotent — returns existing tenant default if already present.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ExtensionPolicy createTenantDefaultPolicy(String tenantId) {
        log.info("Ensuring tenant default ExtensionPolicy for tenantId={}", tenantId);

        // Already exists?
        Optional<ExtensionPolicy> existing =
                extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId);
        if (existing.isPresent()) {
            log.debug("Tenant default ExtensionPolicy already exists for tenantId={}", tenantId);
            return existing.get();
        }

        // Clone from global default
        ExtensionPolicy global = createDefaultPolicyIfNotExists();

        ExtensionPolicy tenantDefault = new ExtensionPolicy();
        tenantDefault.setFkTenantId(tenantId);
        tenantDefault.setTenantDefault(true);
        tenantDefault.setName(global.getName());
        tenantDefault.setDescription(global.getDescription());
        tenantDefault.setPolicyKey(UUID.randomUUID().toString());
        tenantDefault.setVersion("0.1");
        tenantDefault.setIsActive(true);
        tenantDefault.setUrlFilters(new ArrayList<>());

        // Clone global default's ManagedExtension settings
        ManagedExtension me = new ManagedExtension();
        if (global.getManagedExtension() != null) {
            me.setAction(global.getManagedExtension().getAction());
            me.setEnforcementAction(global.getManagedExtension().getEnforcementAction());
            me.setWarningMessage(global.getManagedExtension().getWarningMessage());
        } else {
            me.setAction(ExtensionAction.ALLOW_ALL);
            me.setEnforcementAction(extensionPolicyDefaults.getEnforcementAction());
            me.setWarningMessage(extensionPolicyDefaults.getWarningMessage());
        }
        tenantDefault.setManagedExtension(me);

        ExtensionPolicy saved;
        try {
            saved = extensionPolicyRepository.save(tenantDefault);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Tenant default ExtensionPolicy created concurrently for tenantId={}. Fetching existing.", tenantId);
            return extensionPolicyRepository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Tenant default ExtensionPolicy exists but cannot be fetched for tenantId=" + tenantId));
        }
        log.info("Tenant default ExtensionPolicy created for tenantId={}, policyId={}",
                tenantId, saved.getPkExtensionPolicyId());
        return saved;
    }
}
