package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateIdentityProviderRequest;
import com.secufusion.iam.dto.SsoConfigurationResponse;
import com.secufusion.iam.entity.SsoConfiguration;
import com.secufusion.iam.entity.SsoProviderUrlConfig;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import com.secufusion.iam.repository.SsoProviderUrlConfigRepository;
import com.secufusion.iam.util.JwtUtl;
import com.secufusion.iam.util.KeycloakAdminUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for managing SSO configurations for tenants.
 *
 * <p>Responsibilities:
 * - Create identity providers in Keycloak and persist SSO configuration records
 * - Read SSO configurations for the current tenant
 * - Update / delete SSO configurations
 * - Activate a single provider per tenant (deactivate others)
 * <p>
 * All public methods expect an {@link HttpServletRequest} containing a tenant JWT,
 * which is resolved via {@link JwtUtl#getTenantFromRequest(HttpServletRequest)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SsoConfigurationService {

    private final JwtUtl jwtUtl;
    private final KeycloakAdminUtil kcUtil;
    private final SsoConfigurationRepository repository;
    private final SsoProviderUrlConfigRepository providerUrlConfigRepository;

    /* ---------------- CREATE ---------------- */
    /**
     * Adds a new Identity Provider for the tenant on the request.
     * Deactivates any existing active configurations for the tenant.
     *
     * @param request         the http request containing tenant JWT
     * @param providerRequest creation DTO
     * @return created {@link SsoConfigurationResponse}
     */
    public SsoConfigurationResponse addProviderToTenant(HttpServletRequest request, CreateIdentityProviderRequest providerRequest) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();

        log.info("Creating IdP for tenant={} alias={} providerId={}", tenantId, providerRequest.getAlias(), providerRequest.getProviderId());

        // 1. Populate DTO with provider URLs from database based on providerId
        enrichRequestWithProviderUrls(providerRequest);

        // 2. Deactivate existing active configs for this tenant
        List<SsoConfiguration> activeConfigs = repository.findByFkTenantId(tenantId).stream().filter(c -> "ACTIVE".equalsIgnoreCase(c.getActive())).toList();

        if (!activeConfigs.isEmpty()) {
            activeConfigs.forEach(c -> c.setActive("INACTIVE"));
            for (SsoConfiguration c : activeConfigs) {
                try {
                    kcUtil.disableIdentityProvider(realm, c.getAlias());
                    log.info("Disabled IdP in Keycloak for realm={} alias={}", realm, c.getAlias());
                } catch (Exception e) {
                    log.warn("Failed to disable IdP in Keycloak for realm={} alias={}, continuing", realm, c.getAlias(), e);
                }
            }
            repository.saveAll(activeConfigs);
        }

        // 3. Create the new Identity Provider in Keycloak
        boolean kcSuccess = false;
        String redirectUrl = null;

        try {
            redirectUrl = kcUtil.addIdentityProvider(realm, providerRequest);
            kcSuccess = true;
            log.info("Keycloak IdP created for realm={} alias={}", realm, providerRequest.getAlias());
        } catch (Exception e) {
            log.error("Keycloak provider creation failed for realm={} alias={}, proceeding to persist INACTIVE record", realm, providerRequest.getAlias(), e);
        }

        // 4. Persist to DB (includes the URLs from provider config)
        SsoConfiguration cfg = mapToEntity(providerRequest, tenantId);
        cfg.setRedirectUri(redirectUrl != null ? redirectUrl : providerRequest.getRedirectUri());
        cfg.setActive(kcSuccess ? "ACTIVE" : "INACTIVE");
        SsoConfiguration saved = repository.save(cfg);

        // 5. (Optional) Set as default login ONLY if requested
        if (kcSuccess && Boolean.TRUE.equals(providerRequest.getSetAsDefaultLogin())) {
            try {
                kcUtil.setAsDefaultIdentityProvider(realm, providerRequest.getAlias());
            } catch (Exception e) {
                log.warn("Failed to set default IdP in Keycloak: {}", e.getMessage());
            }
        }

        return SsoConfigurationResponse.from(saved);
    }

    /**
     * Enriches the provider request with URLs from the database based on providerId.
     * Fetches configuration from sso_provider_url_config table and populates the DTO.
     *
     * @param providerRequest the request to enrich
     */
    private void enrichRequestWithProviderUrls(CreateIdentityProviderRequest providerRequest) {
        String providerId = providerRequest.getProviderId();
        if (providerId == null || providerId.isBlank()) {
            providerId = "azure"; // Default to Azure
        }

        log.debug("Fetching URL configuration for providerId={}", providerId);

        Optional<SsoProviderUrlConfig> configOpt = providerUrlConfigRepository
                .findByProviderIdAndEnabled(providerId.toLowerCase(), true);

        if (configOpt.isPresent()) {
            SsoProviderUrlConfig config = configOpt.get();
            log.info("Enriching request with URLs from provider config: {}", config.getDisplayName());

            // Only set if not already provided in the request
            if (providerRequest.getAuthorizationUrl() == null || providerRequest.getAuthorizationUrl().isBlank()) {
                providerRequest.setAuthorizationUrl(config.getAuthorizationUrl());
            }
            if (providerRequest.getTokenUrl() == null || providerRequest.getTokenUrl().isBlank()) {
                providerRequest.setTokenUrl(config.getTokenUrl());
            }
            if (providerRequest.getLogoutUrl() == null || providerRequest.getLogoutUrl().isBlank()) {
                providerRequest.setLogoutUrl(config.getLogoutUrl());
            }
            if (providerRequest.getUserInfoUrl() == null || providerRequest.getUserInfoUrl().isBlank()) {
                providerRequest.setUserInfoUrl(config.getUserInfoUrl());
            }
            if (providerRequest.getJwksUrl() == null || providerRequest.getJwksUrl().isBlank()) {
                providerRequest.setJwksUrl(config.getJwksUrl());
            }
            if (providerRequest.getIssuer() == null) {
                providerRequest.setIssuer(config.getIssuer() != null ? config.getIssuer() : "");
            }
            if (providerRequest.getScopes() == null || providerRequest.getScopes().isBlank()) {
                providerRequest.setScopes(config.getDefaultScopes() != null ? config.getDefaultScopes() : "openid email profile");
            }
        } else {
            log.warn("No provider URL config found for providerId={}, using request values or defaults", providerId);
            // Set defaults if not provided
            if (providerRequest.getScopes() == null || providerRequest.getScopes().isBlank()) {
                providerRequest.setScopes("openid email profile");
            }
            if (providerRequest.getIssuer() == null) {
                providerRequest.setIssuer("");
            }
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

    /* ---------------- UPDATE ---------------- */

    /**
     * Updates an existing SSO configuration for the tenant on the request.
     *
     * @param request the http request containing tenant JWT
     * @param id      configuration id
     * @param dto     update DTO
     * @throws ResourceNotFoundException when not found
     */
    public void update(HttpServletRequest request, String id, CreateIdentityProviderRequest dto) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();
        log.info("Updating SSO configuration id={} for tenant={}", id, tenantId);

        SsoConfiguration cfg = repository.findByIdAndFkTenantId(id, tenantId).orElseThrow(() -> {
            log.warn("SSO configuration not found for update id={} tenant={}", id, tenantId);
            return new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND");
        });

        String oldAlias = cfg.getAlias();
        boolean aliasChanged = oldAlias == null ? dto.getAlias() != null : !oldAlias.equals(dto.getAlias());

        cfg.setAlias(dto.getAlias());
        cfg.setProviderId(dto.getProviderId());
        cfg.setClientId(dto.getClientId());
        cfg.setClientSecret(dto.getClientSecret());
        cfg.setAuthorizationUrl(dto.getAuthorizationUrl());
        cfg.setTokenUrl(dto.getTokenUrl());
        cfg.setUserInfoUrl(dto.getUserInfoUrl());
        cfg.setIssuer(dto.getIssuer());
        cfg.setSetAsDefaultLogin(Boolean.TRUE.equals(dto.getSetAsDefaultLogin()));

        // Persist DB changes first
        repository.save(cfg);

        // If this provider is active, attempt to update Keycloak by deleting old and re-adding
        if ("ACTIVE".equalsIgnoreCase(cfg.getActive())) {
            try {
                if (aliasChanged && oldAlias != null) {
                    try {
                        kcUtil.deleteIdentityProvider(realm, oldAlias);
                        log.info("Removed old IdP from Keycloak realm={} alias={}", realm, oldAlias);
                    } catch (Exception e) {
                        log.warn("Failed to remove old IdP in Keycloak realm={} alias={}, continuing", realm, oldAlias, e);
                    }
                }

                String redirectUrl = kcUtil.addIdentityProvider(realm, dto);
                if (redirectUrl != null) {
                    cfg.setRedirectUri(redirectUrl);
                    repository.save(cfg);
                }

                if (Boolean.TRUE.equals(dto.getSetAsDefaultLogin())) {
                    try {
                        kcUtil.setAsDefaultIdentityProvider(realm, dto.getAlias());
                        log.info("Set IdP as default login in Keycloak for realm={} alias={}", realm, dto.getAlias());
                    } catch (Exception e) {
                        log.warn("Failed to set default IdP in Keycloak realm={} alias={}", realm, dto.getAlias(), e);
                    }
                }

                log.info("Updated IdP in Keycloak for realm={} alias={}", realm, dto.getAlias());
            } catch (Exception e) {
                log.warn("Failed to update IdP in Keycloak for realm={} alias={}, continuing", realm, dto.getAlias(), e);
            }
        }

        log.info("Updated SSO configuration id={} for tenant={}", id, tenantId);
    }

    /* ---------------- DELETE ---------------- */

    /**
     * Deletes an SSO configuration and attempts to remove it from Keycloak.
     *
     * @param request the http request containing tenant JWT
     * @param id      configuration id
     * @throws ResourceNotFoundException when not found
     */
    public void delete(HttpServletRequest request, String id) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();
        log.info("Deleting SSO configuration id={} for tenant={}", id, tenantId);

        SsoConfiguration cfg = repository.findByIdAndFkTenantId(id, tenantId).orElseThrow(() -> {
            log.warn("SSO configuration not found for delete id={} tenant={}", id, tenantId);
            return new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND");
        });

        try {
            kcUtil.deleteIdentityProvider(realm, cfg.getAlias());
            log.info("Deleted IdP from Keycloak realm={} alias={}", realm, cfg.getAlias());
        } catch (Exception e) {
            log.warn("Failed to delete IdP from KC for realm={} alias={}, continuing DB delete", realm, cfg.getAlias(), e);
        }

        repository.delete(cfg);
        log.info("Deleted SSO configuration id={} for tenant={}", id, tenantId);
    }

    /* ---------------- ACTIVATE ---------------- */

    /**
     * Activates the specified configuration and deactivates any other configurations
     * for the tenant. Also attempts to set the provider as default in Keycloak.
     *
     * @param request the http request containing tenant JWT
     * @param id      configuration id
     * @throws ResourceNotFoundException when not found
     */
    public void activate(HttpServletRequest request, String id) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();
        log.info("Activating SSO configuration id={} for tenant={}", id, tenantId);

        deactivateOthers(tenantId);

        SsoConfiguration cfg = repository.findByIdAndFkTenantId(id, tenantId).orElseThrow(() -> {
            log.warn("SSO configuration not found for activate id={} tenant={}", id, tenantId);
            return new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND");
        });

        cfg.setActive("ACTIVE");
        repository.save(cfg);
        log.info("Activated SSO configuration id={} for tenant={}", id, tenantId);

        try {
            kcUtil.setAsDefaultIdentityProvider(realm, cfg.getAlias());
            log.info("Set IdP as default login in Keycloak for realm={} alias={}", realm, cfg.getAlias());
        } catch (Exception e) {
            log.warn("Failed to set default IdP in Keycloak after activation realm={} alias={}", realm, cfg.getAlias(), e);
        }
    }

    /* ---------------- HELPERS ---------------- */

    /**
     * Deactivates all SSO configurations for the given tenant id.
     *
     * @param tenantId target tenant id
     */
    private void deactivateOthers(String tenantId) {
        List<SsoConfiguration> list = repository.findByTenantId(tenantId);
        list.forEach(c -> c.setActive("INACTIVE"));
        repository.saveAll(list);
        log.debug("Deactivated {} SSO configurations for tenant={}", list.size(), tenantId);
    }

    /**
     * Maps a creation DTO to the entity.
     *
     * @param dto      creation DTO
     * @param tenantId tenant id
     * @return mapped entity
     */
    private SsoConfiguration mapToEntity(CreateIdentityProviderRequest dto, String tenantId) {
        SsoConfiguration cfg = new SsoConfiguration();
        cfg.setAlias(dto.getAlias());
        cfg.setProviderId(dto.getProviderId());
        cfg.setTenantId(dto.getTenantId());
        cfg.setClientId(dto.getClientId());
        cfg.setClientSecret(dto.getClientSecret());
        cfg.setAuthorizationUrl(dto.getAuthorizationUrl());
        cfg.setTokenUrl(dto.getTokenUrl());
        cfg.setUserInfoUrl(dto.getUserInfoUrl());
        cfg.setIssuer(dto.getIssuer());
        cfg.setLogoutUrl(dto.getLogoutUrl());
        cfg.setJwksUrl(dto.getJwksUrl());
        cfg.setScopes(dto.getScopes());
        cfg.setFkTenantId(tenantId);
        cfg.setSetAsDefaultLogin(Boolean.TRUE.equals(dto.getSetAsDefaultLogin()));
        return cfg;
    }
}