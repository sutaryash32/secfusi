package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateIdentityProviderRequest;
import com.secufusion.iam.dto.SsoConfigurationResponse;
import com.secufusion.iam.entity.SsoConfiguration;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.GlobalException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.util.JwtUtl;
import com.secufusion.iam.util.KeycloakAdminUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SsoConfigurationService {

    private final JwtUtl jwtUtl;
    private final KeycloakAdminUtil kcUtil;
    private final SsoConfigurationRepository repository;
    private final TenantRepository tenantRepository;

    @Value("${app.sso.governance.min-level:ENTERPRISE}")
    private String minSsoCreationLevel;

    /* ------------------------------------------------------------------
     * CREATE
     * ------------------------------------------------------------------ */

    public SsoConfigurationResponse addProviderToTenant(
            HttpServletRequest request,
            CreateIdentityProviderRequest dto
    ) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();
        boolean providerExists = repository
                .findByTenantId(tenantId)
                .stream()
                .anyMatch(cfg -> cfg.getProviderId().equalsIgnoreCase(dto.getProviderId()));

        if (providerExists) {
            throw new GlobalException(
                    "SSO_PROVIDER_EXISTS",
                    "SSO provider '" + dto.getProviderId() + "' already configured."
            );
        }

        validateSsoGovernance(tenant);

        boolean kcSuccess = false;
        String redirectUrl = null;

        try {
            redirectUrl = kcUtil.addIdentityProvider(realm, dto);
            kcSuccess = true;
        } catch (Exception e) {
            log.error("Keycloak IdP creation failed", e);
        }

        // If marked as DEFAULT → unset default from others
        if (Boolean.TRUE.equals(dto.getSetAsDefaultLogin())) {
            unsetDefaultForOthers(tenantId, null);
        }

        SsoConfiguration cfg = mapToEntity(dto, tenantId);
        cfg.setActive(kcSuccess ? "ACTIVE" : "INACTIVE");
        cfg.setRedirectUri(redirectUrl);

        SsoConfiguration saved = repository.save(cfg);

        // Apply default login in Keycloak
        if (kcSuccess && Boolean.TRUE.equals(dto.getSetAsDefaultLogin())) {
            kcUtil.setAsDefaultIdentityProvider(realm, dto.getAlias());
        }

        // Gateway propagation
        if (isGateway(tenant) && kcSuccess) {
            relinkDescendantsToNewGateway(
                    tenant,
                    Boolean.TRUE.equals(dto.getSetAsDefaultLogin())
            );
        }

        return SsoConfigurationResponse.from(saved);
    }

    /* ------------------------------------------------------------------
     * ACTIVATE (SWITCH DEFAULT LOGIN)
     * ------------------------------------------------------------------ */

    public void activate(HttpServletRequest request, String id) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();

        SsoConfiguration cfg = repository.findByIdAndFkTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND"));

        cfg.setActive("ACTIVE");

        if (Boolean.TRUE.equals(cfg.getSetAsDefaultLogin())) {
            unsetDefaultForOthers(tenantId, cfg.getId());
            kcUtil.setAsDefaultIdentityProvider(realm, cfg.getAlias());
        }

        repository.save(cfg);

        if (isGateway(tenant)) {
            relinkDescendantsToNewGateway(
                    tenant,
                    Boolean.TRUE.equals(cfg.getSetAsDefaultLogin())
            );
        }
    }

    /* ------------------------------------------------------------------
     * UPDATE
     * ------------------------------------------------------------------ */

    public void update(
            HttpServletRequest request,
            String id,
            CreateIdentityProviderRequest dto
    ) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();

        SsoConfiguration cfg = repository.findByIdAndFkTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND"));

        String oldAlias = cfg.getAlias();
        boolean aliasChanged = !oldAlias.equals(dto.getAlias());

        cfg.setAlias(dto.getAlias());
        cfg.setClientId(dto.getClientId());
        cfg.setClientSecret(dto.getClientSecret());
        cfg.setSetAsDefaultLogin(dto.getSetAsDefaultLogin());

        repository.save(cfg);

        if ("ACTIVE".equals(cfg.getActive())) {
            if (aliasChanged) {
                kcUtil.deleteIdentityProvider(realm, oldAlias);
            }
            kcUtil.addIdentityProvider(realm, dto);
        }

        if (Boolean.TRUE.equals(dto.getSetAsDefaultLogin())) {
            unsetDefaultForOthers(tenantId, cfg.getId());
            kcUtil.setAsDefaultIdentityProvider(realm, dto.getAlias());
        }

        if (isGateway(tenant)) {
            relinkDescendantsToNewGateway(
                    tenant,
                    Boolean.TRUE.equals(dto.getSetAsDefaultLogin())
            );
        }
    }

    /* ------------------------------------------------------------------
     * DELETE
     * ------------------------------------------------------------------ */

    public void delete(HttpServletRequest request, String id) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();

        SsoConfiguration cfg = repository.findByIdAndFkTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND"));

        try {
            kcUtil.deleteIdentityProvider(tenant.getRealmName(), cfg.getAlias());
        } catch (Exception ignored) {}

        repository.delete(cfg);

        if (isGateway(tenant) && "ACTIVE".equals(cfg.getActive())) {
            unlinkDescendants(tenant);
        }
    }

    /* ------------------------------------------------------------------
     * DEFAULT HANDLING
     * ------------------------------------------------------------------ */

    private void unsetDefaultForOthers(String tenantId, String exceptId) {
        List<SsoConfiguration> list = repository.findByTenantId(tenantId);
        for (SsoConfiguration cfg : list) {
            if (!cfg.getId().equals(exceptId)
                    && Boolean.TRUE.equals(cfg.getSetAsDefaultLogin())) {
                cfg.setSetAsDefaultLogin(false);
            }
        }
        repository.saveAll(list);
    }

    /* ------------------------------------------------------------------
     * GATEWAY LOGIC (UNCHANGED)
     * ------------------------------------------------------------------ */

    private void relinkDescendantsToNewGateway(Tenant gatewayTenant, boolean setAsDefault) {
        relinkDescendantsRecursive(
                gatewayTenant.getTenantID(),
                gatewayTenant.getRealmName(),
                setAsDefault
        );
    }

    private void relinkDescendantsRecursive(
            String parentTenantId,
            String gatewayRealmName,
            boolean setAsDefault
    ) {

        List<Tenant> children = tenantRepository.findByParentTenantId(parentTenantId);

        for (Tenant child : children) {

            if (child.getRealmName().equals(gatewayRealmName)) {
                continue;
            }

            boolean childHasSso =
                    repository.findByFkTenantId(child.getTenantID()).isPresent();

            if (childHasSso) continue;

            kcUtil.removeIdentityProvider(child.getRealmName(), "parent-gateway");
            kcUtil.linkTenantToGatewayRealm(child, gatewayRealmName);

            if (setAsDefault) {
                kcUtil.setAsDefaultIdentityProvider(
                        child.getRealmName(),
                        "parent-gateway"
                );
            }

            relinkDescendantsRecursive(
                    child.getTenantID(),
                    gatewayRealmName,
                    setAsDefault
            );
        }
    }

    private void unlinkDescendants(Tenant gatewayTenant) {
        unlinkDescendantsRecursive(gatewayTenant.getTenantID());
    }

    private void unlinkDescendantsRecursive(String parentTenantId) {
        List<Tenant> children = tenantRepository.findByParentTenantId(parentTenantId);
        for (Tenant child : children) {
            kcUtil.removeIdentityProvider(child.getRealmName(), "parent-gateway");
            unlinkDescendantsRecursive(child.getTenantID());
        }
    }
    /* ---------------- READ ---------------- */

    /**
     * Returns all SSO configurations for the tenant on the request.
     *
     * @param request the http request containing tenant JWT
     * @return list of {@link SsoConfigurationResponse}
     */
    @Transactional(readOnly = true)
    public List<SsoConfigurationResponse> getAll(HttpServletRequest request) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        log.debug("Fetching all SSO configurations for tenant={}", tenantId);

        List<SsoConfigurationResponse> responses = repository.findByFkTenantId(tenantId).stream().map(SsoConfigurationResponse::from).toList();

        log.debug("Found {} SSO configurations for tenant={}", responses.size(), tenantId);
        return responses;
    }

    /**
     * Returns a single SSO configuration by id for the tenant on the request.
     *
     * @param request the http request containing tenant JWT
     * @param id      configuration id
     * @return {@link SsoConfigurationResponse}
     * @throws ResourceNotFoundException when not found
     */
    @Transactional(readOnly = true)
    public SsoConfiguration getById(HttpServletRequest request, String id) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        log.debug("Fetching SSO configuration id={} for tenant={}", id, tenantId);

        SsoConfiguration cfg = repository.findByIdAndFkTenantId(id, tenantId).orElseThrow(() -> {
            log.warn("SSO configuration not found id={} tenant={}", id, tenantId);
            return new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND");
        });

        log.debug("Returning SSO configuration id={} for tenant={}", id, tenantId);
        return cfg;
    }

    /* ------------------------------------------------------------------
     * HELPERS
     * ------------------------------------------------------------------ */

    private boolean isGateway(Tenant tenant) {
        return switch (tenant.getTenantType()) {
            case "MSSP", "MASTER_MSSP", "MAIN_MASTER_MSSP" -> true;
            default -> false;
        };
    }

    private void validateSsoGovernance(Tenant tenant) {
        int currentRank = getTenantRank(tenant.getTenantType());
        int requiredRank = getTenantRank(minSsoCreationLevel);
        if (currentRank < requiredRank) {
            throw new GlobalException("SSO_CREATION_RESTRICTED",
                    "Tenant not authorized to configure SSO");
        }
    }

    private int getTenantRank(String tenantType) {
        return switch (tenantType.toUpperCase()) {
            case "MAIN_MASTER_MSSP" -> 4;
            case "MASTER_MSSP" -> 3;
            case "MSSP" -> 2;
            case "ENTERPRISE" -> 1;
            default -> 0;
        };
    }

    private SsoConfiguration mapToEntity(CreateIdentityProviderRequest dto, String tenantId) {
        SsoConfiguration cfg = new SsoConfiguration();
        cfg.setAlias(dto.getAlias());
        cfg.setProviderId(dto.getProviderId());
        cfg.setClientId(dto.getClientId());
        cfg.setClientSecret(dto.getClientSecret());
        cfg.setScopes(dto.getScopes());
        cfg.setIssuer(dto.getIssuer());
        cfg.setTenantId(dto.getTenantId());
        cfg.setFkTenantId(tenantId);
        cfg.setSetAsDefaultLogin(Boolean.TRUE.equals(dto.getSetAsDefaultLogin()));
        return cfg;
    }
}
