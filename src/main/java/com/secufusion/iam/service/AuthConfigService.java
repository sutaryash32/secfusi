package com.secufusion.iam.service;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.entity.AuthProviderConfig;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AuthProviderConfigRepository;
import com.secufusion.iam.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for loading tenant auth configuration and creating JWT decoders.
 */
@Service
@Slf4j
public class AuthConfigService {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuthProviderConfigRepository authProviderConfigRepository;

    /**
     * Load authentication details for a tenant identified by host (domain or tenantName).
     *
     * @param host domain or tenantName to look up
     * @return populated AuthDetailsDto
     * @throws ResourceNotFoundException when tenant or provider config is missing
     */
    public AuthDetailsDto getTenantConfig(String host) {
        log.info("Fetching tenant config for host={}", host);

        // Try lookup by domain first, then by tenantName to allow both forms of incoming host values.
        Tenant tenant;
        var domainOpt = tenantRepository.findByDomain(host);
        if (domainOpt.isPresent()) {
            tenant = domainOpt.get();
            log.debug("Tenant found by domain. tenantId={}, tenantName={}", tenant.getTenantID(), tenant.getTenantName());
        } else {
            var nameOpt = tenantRepository.findByTenantName(host);
            if (nameOpt.isPresent()) {
                tenant = nameOpt.get();
                log.debug("Tenant found by tenantName fallback. tenantId={}, tenantName={}", tenant.getTenantID(), tenant.getTenantName());
            } else {
                log.warn("Tenant not found for host={}", host);
                throw new ResourceNotFoundException("Tenant not found for: " + host);
            }
        }

        AuthProviderConfig cfg = authProviderConfigRepository.findByTenant(tenant)
                .orElseThrow(() -> {
                    log.warn("Auth provider config missing for tenantId={}", tenant.getTenantID());
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
}