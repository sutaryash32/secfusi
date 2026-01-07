package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateIdentityProviderRequest;
import com.secufusion.iam.dto.SsoConfigurationResponse;
import com.secufusion.iam.entity.SsoConfiguration;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import com.secufusion.iam.util.JwtUtl;
import com.secufusion.iam.util.KeycloakAdminUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    /* ---------------- CREATE ---------------- */

    /**
     * Adds an identity provider for the tenant resolved from the given request.
     * <p>
     * Attempts to create the provider in Keycloak first; the DB record is persisted
     * regardless of Keycloak success, but the record's active state and redirect URI
     * reflect the Keycloak outcome.
     *
     * @param request         the http request containing tenant JWT
     * @param providerRequest the provider creation DTO
     * @return persisted {@link SsoConfigurationResponse}
     */
    public SsoConfigurationResponse addProviderToTenant(
            HttpServletRequest request,
            CreateIdentityProviderRequest providerRequest) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        String realm = tenant.getRealmName();

        log.info("Creating IdP for tenant={} alias={} providerId={}", tenantId, providerRequest.getAlias(), providerRequest.getProviderId());

        boolean kcSuccess = false;
        String redirectUrl = null;

        try {
            redirectUrl = kcUtil.addIdentityProvider(realm, providerRequest);
            kcSuccess = true;
            log.info("Keycloak IdP created for realm={} alias={}", realm, providerRequest.getAlias());
        } catch (Exception e) {
            log.error("Keycloak provider creation failed for realm={} alias={}, proceeding to persist INACTIVE record", realm, providerRequest.getAlias(), e);
        }

        SsoConfiguration cfg = mapToEntity(providerRequest, tenantId);
        cfg.setRedirectUri(
                redirectUrl != null ? redirectUrl : providerRequest.getRedirectUri()
        );
        cfg.setActive(kcSuccess ? "ACTIVE" : "INACTIVE");

        // Ensure only one ACTIVE provider per tenant
        if ("ACTIVE".equalsIgnoreCase(cfg.getActive())) {
            deactivateOthers(tenantId);
        }

        // Save DB record
        SsoConfiguration saved = repository.save(cfg);
        log.info("SSO configuration saved for tenant={} id={} active={}", tenantId, saved.getId(), saved.getActive());

        // Set default login in Keycloak if requested
        if (kcSuccess && Boolean.TRUE.equals(providerRequest.getSetAsDefaultLogin())) {
            try {
                kcUtil.setAsDefaultIdentityProvider(realm, providerRequest.getAlias());
                log.info("Set IdP as default login in Keycloak for realm={} alias={}", realm, providerRequest.getAlias());
            } catch (Exception e) {
                log.warn("Failed to set default IdP in Keycloak for realm={} alias={}", realm, providerRequest.getAlias(), e);
            }
        }

        return SsoConfigurationResponse.from(saved);
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

        List<SsoConfigurationResponse> responses = repository.findByTenantId(tenantId)
                .stream()
                .map(SsoConfigurationResponse::from)
                .toList();

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
    public SsoConfigurationResponse getById(HttpServletRequest request, String id) {
        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        log.debug("Fetching SSO configuration id={} for tenant={}", id, tenantId);

        SsoConfiguration cfg = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    log.warn("SSO configuration not found id={} tenant={}", id, tenantId);
                    return new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND");
                });

        log.debug("Returning SSO configuration id={} for tenant={}", id, tenantId);
        return SsoConfigurationResponse.from(cfg);
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
    public void update(HttpServletRequest request,
                       String id,
                       CreateIdentityProviderRequest dto) {

        Tenant tenant = jwtUtl.getTenantFromRequest(request);
        String tenantId = tenant.getTenantID();
        log.info("Updating SSO configuration id={} for tenant={}", id, tenantId);

        SsoConfiguration cfg = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    log.warn("SSO configuration not found for update id={} tenant={}", id, tenantId);
                    return new ResourceNotFoundException("SSO_CONFIG_NOT_FOUND");
                });

        cfg.setClientId(dto.getClientId());
        cfg.setClientSecret(dto.getClientSecret());
        cfg.setAuthorizationUrl(dto.getAuthorizationUrl());
        cfg.setTokenUrl(dto.getTokenUrl());
        cfg.setUserInfoUrl(dto.getUserInfoUrl());
        cfg.setIssuer(dto.getIssuer());
        cfg.setSetAsDefaultLogin(Boolean.TRUE.equals(dto.getSetAsDefaultLogin()));

        repository.save(cfg);
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

        SsoConfiguration cfg = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
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

        SsoConfiguration cfg = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
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
        cfg.setTenantId(tenantId);
        cfg.setClientId(dto.getClientId());
        cfg.setClientSecret(dto.getClientSecret());
        cfg.setAuthorizationUrl(dto.getAuthorizationUrl());
        cfg.setTokenUrl(dto.getTokenUrl());
        cfg.setUserInfoUrl(dto.getUserInfoUrl());
        cfg.setIssuer(dto.getIssuer());
        cfg.setSetAsDefaultLogin(Boolean.TRUE.equals(dto.getSetAsDefaultLogin()));
        return cfg;
    }
}