package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.NetworkPolicyRepository;
import com.secufusion.tenant.repository.PolicyAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that manages NetworkPolicy lifecycle: create, read, update and delete.
 *
 * <p>Responsibilities:
 * - Map DTOs to JPA entities and vice-versa.
 * - Maintain bidirectional relationships between NetworkPolicy and UrlFilter.
 * - Persist entities via {@link NetworkPolicyRepository}.
 * <p>Policy Versioning:
 * - CREATE: creates initial version 0.1
 * - UPDATE: deactivates old version and creates a new row with incremented version
 * - READ: returns only ACTIVE policies
 *
 * <p>All public methods are transactional by default (class-level {@code @Transactional}).
 */
@Slf4j
@Service
@Transactional
public class NetworkPolicyService {

    private final NetworkPolicyRepository repository;
    private final PolicyAssignmentRepository policyAssignmentRepository;

    @Autowired
    public NetworkPolicyService(
            NetworkPolicyRepository repository,
            PolicyAssignmentRepository policyAssignmentRepository) {
        this.repository = repository;
        this.policyAssignmentRepository = policyAssignmentRepository;
    }

    /* ===================== CREATE ===================== */

    /**
     * Create a new NetworkPolicy for the given tenant.
     *
     * @param dto      request payload
     * @param tenantId tenant identifier
     * @return created NetworkPolicy as response DTO
     */
    public NetworkPolicyResponseDTO create(
            NetworkPolicyRequestDTO dto,
            String tenantId) {

        log.debug("create() start tenantId={} dto={}", tenantId, dto);

        NetworkPolicy policy = mapToEntity(dto, tenantId);

        // 🔑 Versioning fields (INITIAL VERSION)
        policy.setPolicyKey(UUID.randomUUID().toString());
        policy.setVersion("0.1");
        policy.setActive(true);

        // ✅ CRITICAL: Link both sides of bidirectional relationship
        linkUrlFiltersToPolicy(policy);

        NetworkPolicy saved = repository.save(policy);

        log.info(
                "✅ NetworkPolicy created successfully. policyId={}, policyKey={}, version={}",
                saved.getPkNetworkPolicyId(),
                saved.getPolicyKey(),
                saved.getVersion()
        );

        log.debug("create() completed id={} createdAt={}",
                saved.getPkNetworkPolicyId(),
                saved.getCreatedAt());

        return mapToResponse(saved);
    }

    /* ===================== DEFAULT POLICY ===================== */

    /**
     * Create default network policy if it doesn't exist (tenant_id = NULL)
     * This is a global fallback policy applied when tenant has no specific policy
     *
     * @return Default NetworkPolicy
     */
    @Transactional
    public NetworkPolicy createDefaultPolicyIfNotExists() {
        final String DEFAULT_POLICY_NAME = "Default Network Policy";

        // 1️⃣ Global default already exists
        Optional<NetworkPolicy> existingGlobal =
                repository.findTopByFkTenantIdIsNull();

        if (existingGlobal.isPresent()) {
            log.debug("Global default NetworkPolicy already exists: {}", existingGlobal.get().getPkNetworkPolicyId());
            return existingGlobal.get();
        }

        // 2️⃣ Create global default policy
        log.info("Creating global default NetworkPolicy (fk_tenant_id=NULL)");

        NetworkPolicy defaultPolicy = new NetworkPolicy();
        defaultPolicy.setFkTenantId(null); // NULL = global default
        defaultPolicy.setName(DEFAULT_POLICY_NAME);
        defaultPolicy.setDescription("System-wide default network policy applied when no tenant-specific policy exists");
        defaultPolicy.setEnabled(true);
        defaultPolicy.setPolicyKey(UUID.randomUUID().toString());
        defaultPolicy.setVersion("0.1");
        defaultPolicy.setActive(true);

        // Empty network configuration (permissive default)
        NetworkConfiguration defaultConfig = new NetworkConfiguration();
        defaultConfig.setProxyMode("direct");
        defaultConfig.setUseSecufusionIdpProxy(false);
        defaultPolicy.setNetworkConfiguration(defaultConfig);

        // Empty URL filters list
        defaultPolicy.setUrlFilters(new java.util.ArrayList<>());

        try {
            NetworkPolicy saved = repository.save(defaultPolicy);
            log.info("✅ Global default NetworkPolicy created: {}", saved.getPkNetworkPolicyId());
            return saved;
        } catch (Exception ex) {
            // 3️⃣ Concurrency fallback
            log.warn("Default NetworkPolicy created concurrently. Fetching existing one.");
            return repository.findTopByFkTenantIdIsNull()
                    .orElseThrow(() ->
                            new IllegalStateException("Default NetworkPolicy exists but cannot be fetched"));
        }
    }

    /* ===================== READ ===================== */

    /**
     * Retrieve a single NetworkPolicy by id for given tenant.
     *
     * @param id       policy id
     * @param tenantId tenant id (currently unused in fetch but kept for API symmetry)
     * @return NetworkPolicyResponseDTO
     */
    @Transactional(readOnly = true)
    public NetworkPolicyResponseDTO getById(String id, String tenantId) {
        log.debug("getById() id={} tenantId={}", id, tenantId);
        Optional<NetworkPolicy> policy = repository.findById(id);
        if (policy.isEmpty()) {
            log.warn("getById() id={} not found, falling back to active default for tenant={}", id, tenantId);
            policy = repository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId);
        }
        return mapToResponse(policy.orElseThrow(() ->
                new IllegalArgumentException("NetworkPolicy not found: " + id)));
    }

    /**
     * Retrieve all ACTIVE NetworkPolicies for a tenant (latest versions only).
     *
     * @param tenantId tenant id
     * @return list of NetworkPolicyResponseDTO
     */
    @Transactional
    public List<NetworkPolicyResponseDTO> getAll(String tenantId) {
        log.debug("getAll() tenantId={}", tenantId);
        List<NetworkPolicy> result = new java.util.ArrayList<>(repository.findAllByFkTenantIdAndIsActiveTrue(tenantId));

        // Add default policy (tenant default preferred, else global)
        NetworkPolicy defaultPolicy = getDefaultPolicyForTenant(tenantId);
        boolean alreadyPresent = result.stream()
                .anyMatch(p -> p.getPkNetworkPolicyId().equals(defaultPolicy.getPkNetworkPolicyId()));
        if (!alreadyPresent) {
            result.add(defaultPolicy);
        }

        List<NetworkPolicyResponseDTO> collect = result.stream()
                .filter(p -> p.getCreatedAt() != null)
                .sorted(Comparator.comparing(NetworkPolicy::getCreatedAt).reversed())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        log.info("getAll() tenantId={} returnedCount={}", tenantId, result.size());
        return collect;
    }

    /**
     * Get network policy by ID with tenant validation
     * Used by PolicyAssignmentService for API key group assignments
     *
     * @param tenantId Tenant ID
     * @param policyId Network policy ID
     * @return NetworkPolicy entity
     */
    @Transactional(readOnly = true)
    public NetworkPolicy getNetworkPolicyById(String tenantId, String policyId) {
        log.debug("getNetworkPolicyById() policyId={} tenantId={}", policyId, tenantId);
        NetworkPolicy policy = fetch(policyId);

        // Validate policy belongs to tenant
        if (!tenantId.equals(policy.getFkTenantId())) {
            log.warn("getNetworkPolicyById() policy {} does not belong to tenant {}", policyId, tenantId);
            throw new IllegalArgumentException("Network policy not found");
        }

        return policy;
    }

    /**
     * Returns the default NetworkPolicy for a tenant.
     * Priority: tenant-specific default (lazy-created) → global default.
     */
    @Transactional
    public NetworkPolicy getDefaultPolicyForTenant(String tenantId) {
        log.debug("getDefaultPolicyForTenant() tenantId={}", tenantId);

        if (tenantId != null) {
            // 1. Check for existing tenant default
            Optional<NetworkPolicy> tenantDefault =
                    repository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId);
            if (tenantDefault.isPresent()) {
                return repository.findByIdWithRelations(tenantDefault.get().getPkNetworkPolicyId())
                        .orElse(tenantDefault.get());
            }

            // 2. Lazy-create tenant default (runs only once per tenant)
            log.info("No tenant default NetworkPolicy found for tenantId={}. Creating one.", tenantId);
            NetworkPolicy created = createTenantDefaultPolicy(tenantId);
            return repository.findByIdWithRelations(created.getPkNetworkPolicyId())
                    .orElse(created);
        }

        // 3. No tenant context — global default
        NetworkPolicy globalDefault = createDefaultPolicyIfNotExists();
        return repository.findByIdWithRelations(globalDefault.getPkNetworkPolicyId())
                .orElse(globalDefault);
    }

    /**
     * Creates a tenant-specific default NetworkPolicy by cloning the global default.
     * Idempotent — returns existing tenant default if already present.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public NetworkPolicy createTenantDefaultPolicy(String tenantId) {
        log.info("Ensuring tenant default NetworkPolicy for tenantId={}", tenantId);

        // Already exists?
        Optional<NetworkPolicy> existing =
                repository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId);
        if (existing.isPresent()) {
            log.debug("Tenant default NetworkPolicy already exists for tenantId={}", tenantId);
            return existing.get();
        }

        // Clone from global default
        NetworkPolicy global = createDefaultPolicyIfNotExists();

        NetworkPolicy tenantDefault = new NetworkPolicy();
        tenantDefault.setFkTenantId(tenantId);
        tenantDefault.setTenantDefault(true);
        tenantDefault.setName(global.getName());
        tenantDefault.setDescription(global.getDescription());
        tenantDefault.setEnabled(global.isEnabled());
        tenantDefault.setActive(true);
        tenantDefault.setPolicyKey(UUID.randomUUID().toString());
        tenantDefault.setVersion("0.1");

        // Clone network configuration
        NetworkConfiguration defaultConfig = new NetworkConfiguration();
        defaultConfig.setProxyMode("direct");
        defaultConfig.setUseSecufusionIdpProxy(false);
        tenantDefault.setNetworkConfiguration(defaultConfig);

        tenantDefault.setUrlFilters(new java.util.ArrayList<>());

        NetworkPolicy saved;
        try {
            saved = repository.save(tenantDefault);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Tenant default NetworkPolicy created concurrently for tenantId={}. Fetching existing.", tenantId);
            return repository.findTopByFkTenantIdAndIsTenantDefaultTrueAndIsActiveTrue(tenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Tenant default NetworkPolicy exists but cannot be fetched for tenantId=" + tenantId));
        }
        log.info("Tenant default NetworkPolicy created for tenantId={}, policyId={}",
                tenantId, saved.getPkNetworkPolicyId());
        return saved;
    }

    /**
     * Get effective network policy for a user based on their group/role assignments.
     * Falls back to default policy if no assignment matches.
     *
     * @param tenantId  Tenant ID
     * @param roleNames User's role names from JWT
     * @param groupIds  User's group IDs (resolved EventsGroup PKs)
     * @return Effective NetworkPolicy (never null)
     */
    @Transactional
    public NetworkPolicy getEffectivePolicy(String tenantId, List<String> roleNames, List<String> groupIds) {
        log.debug("Determining effective NetworkPolicy for Tenant: {} | Roles: {} | Groups: {}",
                tenantId, roleNames != null ? roleNames.size() : 0, groupIds != null ? groupIds.size() : 0);

        try {
            List<String> safeRoles = (roleNames == null) ? Collections.emptyList() : roleNames;
            List<String> safeGroups = (groupIds == null) ? Collections.emptyList() : groupIds;

            if (!safeRoles.isEmpty() || !safeGroups.isEmpty()) {
                List<PolicyAssignment> matches = policyAssignmentRepository
                        .findEffectiveAssignments(tenantId, safeGroups, safeRoles);

                if (matches != null && !matches.isEmpty()) {
                    PolicyAssignment match = matches.stream()
                            .filter(Objects::nonNull)
                            .filter(a -> a.getNetworkPolicy() != null)
                            .findFirst()
                            .orElse(null);

                    if (match != null) {
                        NetworkPolicy matched = match.getNetworkPolicy();
                        log.info("Found NetworkPolicy: '{}' via Assignment ID: {} (Type: {})",
                                matched.getName(), match.getId(), match.getAssignmentType());
                        // Re-fetch with relations to avoid LazyInitializationException
                        return repository.findByIdWithRelations(matched.getPkNetworkPolicyId())
                                .orElse(matched);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error determining effective NetworkPolicy for tenant {}. Reason: {}",
                    tenantId, e.getMessage(), e);
        }

        log.info("Falling back to default NetworkPolicy for tenant: {}", tenantId);
        return getDefaultPolicyForTenant(tenantId);
    }

    /* ===================== UPDATE ===================== */

    /**
     * Update an existing NetworkPolicy using VERSIONING.
     *
     * <p>Behavior:
     * 1. Deactivate existing policy row
     * 2. Create a NEW row with same policyKey
     * 3. Increment version (0.1 → 0.2 → 1.0 ...)
     */
    public NetworkPolicyResponseDTO update(
            String id,
            NetworkPolicyRequestDTO dto,
            String tenantId) {

        log.debug("update() start id={} tenantId={} dto={}", id, tenantId, dto);

        NetworkPolicy current = fetch(id);

        // Guard: prevent editing global default policy
        if (current.isGlobalDefault()) {
            throw new IllegalArgumentException("Cannot modify the global default Network Policy");
        }

        log.trace("update() current policyKey={} version={}",
                current.getPolicyKey(),
                current.getVersion());

        // 1️⃣ Deactivate old version — flush immediately so the DB sees is_active=FALSE
        //    before the new version INSERT hits the V19 unique index check.
        current.setActive(false);
        repository.saveAndFlush(current);

        // 2️⃣ Create new version row
        NetworkPolicy newVersion = new NetworkPolicy();

        newVersion.setPolicyKey(current.getPolicyKey());
        newVersion.setVersion(nextMinorVersion(current.getVersion()));
        newVersion.setActive(true);

        newVersion.setFkTenantId(current.getFkTenantId());
        newVersion.setTenantDefault(current.isTenantDefault());
        newVersion.setName(dto.getName());
        newVersion.setDescription(dto.getDescription());
        newVersion.setEnabled(dto.getEnabled());

        // URL Filters
        if (dto.getUrlFilters() != null) {
            List<UrlFilter> filters = mapUrlFilters(dto.getUrlFilters());
            newVersion.setUrlFilters(filters);
            linkUrlFiltersToPolicy(newVersion, filters);
        } else {
            // Carry forward existing filters — clear IDs to create new records for the new version
            List<UrlFilter> carriedFilters = new java.util.ArrayList<>();
            if (current.getUrlFilters() != null) {
                for (UrlFilter existing : current.getUrlFilters()) {
                    UrlFilter copy = new UrlFilter();
                    copy.setFilterType(existing.getFilterType());
                    copy.setPatternType(existing.getPatternType());
                    copy.setPattern(existing.getPattern());
                    copy.setDescription(existing.getDescription());
                    carriedFilters.add(copy);
                }
            }
            newVersion.setUrlFilters(carriedFilters);
            linkUrlFiltersToPolicy(newVersion, carriedFilters);
        }

        // Network Configuration — clear ID when carrying forward to create a new record
        if (dto.getNetworkConfiguration() != null) {
            newVersion.setNetworkConfiguration(mapNetworkConfiguration(dto.getNetworkConfiguration()));
        } else if (current.getNetworkConfiguration() != null) {
            NetworkConfiguration carriedConfig = current.getNetworkConfiguration();
            carriedConfig.setPkNetworkConfigurationId(null);
            newVersion.setNetworkConfiguration(carriedConfig);
        }

        NetworkPolicy saved = repository.save(newVersion);

        // Migrate PolicyAssignment references from old version to new version
        policyAssignmentRepository.updateNetworkPolicyReference(
                current.getPkNetworkPolicyId(),
                saved.getPkNetworkPolicyId()
        );

        log.info(
                "🔄 NetworkPolicy version updated. policyKey={} oldVersion={} newVersion={}",
                current.getPolicyKey(),
                current.getVersion(),
                saved.getVersion()
        );

        log.debug("update() completed newPolicyId={} updatedAt={}",
                saved.getPkNetworkPolicyId(),
                saved.getUpdatedAt());

        return mapToResponse(saved);
    }

    /**
     * Version increment helper.
     * Example: 0.1 → 0.2 → 1.0
     */
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

    /* ===================== DELETE ===================== */

    /**
     * Delete a NetworkPolicy by id.
     *
     * @param id       policy id
     * @param tenantId tenant id (kept for API symmetry)
     */
    public void delete(String id, String tenantId) {
        log.debug("delete() start id={} tenantId={}", id, tenantId);
        NetworkPolicy existing = fetch(id);

        // Guard: prevent deleting global default policy
        if (existing.isGlobalDefault()) {
            throw new IllegalArgumentException("Cannot delete the global default Network Policy");
        }

        // Guard: prevent deleting tenant default policy
        if (existing.isTenantDefault()) {
            throw new IllegalArgumentException("Cannot delete the tenant default Network Policy. You can edit it instead.");
        }

        // Validate tenant ownership
        if (existing.getFkTenantId() != null && !tenantId.equals(existing.getFkTenantId())) {
            log.warn("delete() policy {} does not belong to tenant {}", id, tenantId);
            throw new IllegalArgumentException("NetworkPolicy not found: " + id);
        }

        repository.delete(existing);
        log.info("✅ NetworkPolicy deleted id={} tenantId={}", id, tenantId);
    }

    /* ===================== INTERNAL HELPERS ===================== */

    private NetworkPolicy fetch(String id) {
        log.debug("fetch() id={}", id);
        return repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("fetch() NetworkPolicy not found id={}", id);
                    return new IllegalArgumentException("NetworkPolicy not found: " + id);
                });
    }

    /* ===================== RELATIONSHIP HELPERS ===================== */

    /**
     * Links URL filters to policy (BOTH sides of bidirectional relationship)
     */
    private void linkUrlFiltersToPolicy(NetworkPolicy policy) {
        if (policy.getUrlFilters() != null) {
            policy.getUrlFilters().forEach(filter -> {
                        filter.setNetworkPolicy(policy);
                    }
            );
            log.trace("linkUrlFiltersToPolicy() linked count={} for policyId={}", policy.getUrlFilters().size(), policy.getPkNetworkPolicyId());
        } else {
            log.trace("linkUrlFiltersToPolicy() no urlFilters to link for policyId={}", policy.getPkNetworkPolicyId());
        }
    }

    private void linkUrlFiltersToPolicy(NetworkPolicy policy, List<UrlFilter> filters) {
        if (filters != null && !filters.isEmpty()) {
            filters.forEach(filter -> filter.setNetworkPolicy(policy));
            log.trace("linkUrlFiltersToPolicy(policy,filters) linked count={} for policyId={}", filters.size(), policy.getPkNetworkPolicyId());
        } else {
            log.trace("linkUrlFiltersToPolicy(policy,filters) no filters to link for policyId={}", policy.getPkNetworkPolicyId());
        }
    }

    /* ===================== MAPPERS ===================== */

    private NetworkPolicy mapToEntity(NetworkPolicyRequestDTO dto, String tenantId) {
        log.trace("mapToEntity() tenantId={} dto={}", tenantId, dto);
        NetworkPolicy policy = new NetworkPolicy();
        policy.setFkTenantId(tenantId);
        policy.setName(dto.getName());
        policy.setDescription(dto.getDescription());
        policy.setEnabled(dto.getEnabled());

        // Map URL filters WITHOUT setting relationship (done in linkUrlFiltersToPolicy)
        if (dto.getUrlFilters() != null) {
            policy.setUrlFilters(mapUrlFilters(dto.getUrlFilters()));
            log.trace("mapToEntity() mapped urlFilters count={}", policy.getUrlFilters().size());
        }

        policy.setNetworkConfiguration(mapNetworkConfiguration(dto.getNetworkConfiguration()));
        return policy;
    }

    private List<UrlFilter> mapUrlFilters(List<UrlFilterDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            log.trace("mapUrlFilters() received empty or null dtos");
            return new java.util.ArrayList<>();  // Return empty list, not null
        }

        List<UrlFilter> mapped = dtos.stream().map(d -> {
            UrlFilter filter = new UrlFilter();
            filter.setFilterType(d.getFilterType());
            filter.setPatternType(d.getPatternType());
            filter.setPattern(d.getPattern());
            filter.setDescription(d.getDescription());
            return filter;
        }).collect(Collectors.toList());

        log.trace("mapUrlFilters() mappedCount={}", mapped.size());
        return mapped;
    }

    private NetworkConfiguration mapNetworkConfiguration(NetworkConfigurationDTO dto) {
        if (dto == null) {
            log.trace("mapNetworkConfiguration() dto is null");
            return null;
        }

        NetworkConfiguration nc = new NetworkConfiguration();
        nc.setProxyMode(dto.getProxyMode());
        nc.setPacUrl(dto.getPacUrl());
        nc.setProxyServers(dto.getProxyServers());
        nc.setBypassList(dto.getBypassList());
        nc.setUseSecufusionIdpProxy(Boolean.TRUE.equals(dto.getUseSecufusionIdpProxy()));
        nc.setCustomIdpHostnames(dto.getCustomIdpHostnames());
        nc.setIdentityProvider(dto.getIdentityProvider());
        nc.setHostnames(dto.getHostnames());
        log.trace("mapNetworkConfiguration() mapped proxyMode={} pacUrl={}", dto.getProxyMode(), dto.getPacUrl());

        return nc;
    }

    private NetworkConfigurationDTO mapNetworkConfigurationToDto(NetworkConfiguration nc) {
        if (nc == null) return null;
        NetworkConfigurationDTO dto = new NetworkConfigurationDTO();
        dto.setProxyMode(nc.getProxyMode());
        dto.setPacUrl(nc.getPacUrl());
        dto.setProxyServers(nc.getProxyServers());
        dto.setBypassList(nc.getBypassList());
        dto.setUseSecufusionIdpProxy(nc.isUseSecufusionIdpProxy());
        dto.setCustomIdpHostnames(nc.getCustomIdpHostnames());
        // ✅ NEW FIELDS
        dto.setIdentityProvider(nc.getIdentityProvider());
        dto.setHostnames(String.valueOf(nc.getHostnames()));
        return dto;
    }

    private List<UrlFilterDTO> mapUrlFiltersToDto(List<UrlFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return filters.stream().map(filter -> {
            UrlFilterDTO dto = new UrlFilterDTO();
            dto.setFilterType(filter.getFilterType());
            dto.setPatternType(filter.getPatternType());
            dto.setPattern(filter.getPattern());
            dto.setDescription(filter.getDescription());
            return dto;
        }).collect(Collectors.toList());
    }

    public NetworkPolicyResponseDTO mapToResponsePublic(NetworkPolicy policy) {
        return mapToResponse(policy);
    }

    private NetworkPolicyResponseDTO mapToResponse(NetworkPolicy policy) {
        if (policy == null) {
            log.trace("mapToResponse() received null policy");
            return null;
        }
        NetworkPolicyResponseDTO dto = NetworkPolicyResponseDTO.builder()
                .networkPolicyId(policy.getPkNetworkPolicyId())
                .name(policy.getName())
                .description(policy.getDescription())
                .enabled(policy.isEnabled())
                .globalDefault(policy.isGlobalDefault())
                .tenantDefault(policy.isTenantDefault())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .urlFilters(mapUrlFiltersToDto(policy.getUrlFilters()))
                .build();
        dto.setNetworkConfiguration(
                policy.getNetworkConfiguration() != null
                        ? mapNetworkConfigurationToDto(policy.getNetworkConfiguration())
                        : null
        );

        log.trace("mapToResponse() policyId={} name={}", policy.getPkNetworkPolicyId(), policy.getName());
        return dto;
    }
}