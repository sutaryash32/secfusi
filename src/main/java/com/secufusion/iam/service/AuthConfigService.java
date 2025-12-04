package com.secufusion.iam.service;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.dto.LoginResponseDto;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AuthProviderConfigRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Autowired
    private JwtUtl jwtUtil;

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

    /**
     * Handle login by validating the token against the request and mapping the resolved User
     * entity into a LoginResponseDto.
     * <p>
     * Extensive logging is performed for request validation, mapping steps and exception cases.
     *
     * @param request incoming HTTP request
     * @param token   bearer token (raw)
     * @return LoginResponseDto populated from resolved user
     * @throws ResourceNotFoundException when token validation fails or required data is missing
     */
    public LoginResponseDto login(HttpServletRequest request, String token) {
        log.info("login - start");
        log.debug("login - request remoteAddr={}, tokenPresent={}", request != null ? request.getRemoteAddr() : "null",
                token != null);

        try {
            // Validate token against request
            log.debug("Validating request token");
            if (!jwtUtil.validateRequestToken(request, token)) {
                log.warn("Token validation failed for request from {}", request != null ? request.getRemoteAddr() : "unknown");
                throw new ResourceNotFoundException("Invalid or missing token");
            }
            log.info("Token validated successfully (token length={})", token != null ? token.length() : 0);

            // Extract user from token/request
            log.debug("Extracting user from request");
            User userFromRequest = jwtUtil.getUserFromRequest(request);
            if (userFromRequest == null) {
                log.error("User extraction returned null after token validation");
                throw new ResourceNotFoundException("User not found in token/request");
            }
            log.info("User extracted: pkUserId={}, userName={}", userFromRequest.getPkUserId(), userFromRequest.getUserName());

            // Map user to response DTO
            LoginResponseDto response = new LoginResponseDto();
            response.setUserId(userFromRequest.getPkUserId());
            response.setUsername(userFromRequest.getUserName());
            response.setEmail(userFromRequest.getEmail());
            response.setFirstName(userFromRequest.getFirstName());
            response.setLastName(userFromRequest.getLastName());
            response.setAccessToken(token); // Do not log token content
            response.setUserType(userFromRequest.getTenant().getTenantType());

            // Convert mapped groups → Set<GroupsLean>
            log.debug("Mapping user groups and roles");
            Set<GroupsLean> mappedGroups =
                    Optional.ofNullable(userFromRequest.getMappedGroups())
                            .orElse(Collections.emptySet())
                            .stream()
                            .map(group -> {

                                // Convert mapped roles → Set<RolesLean>
                                Set<RolesLean> roles =
                                        Optional.ofNullable(group.getMappedRoles())
                                                .orElse(Collections.emptySet())
                                                .stream()
                                                .map(role ->
                                                        new RolesLean(
                                                                role.getPkRoleId(),
                                                                role.getName()
                                                        )
                                                )
                                                .collect(Collectors.toSet());

                                // Convert Group → GroupsLean with roles
                                return new GroupsLean(
                                        group.getPkGroupId(),
                                        group.getName(),
                                        roles
                                );
                            })
                            .collect(Collectors.toSet());

            response.setMappedGroups(mappedGroups);

            // Ensure tenant is available on user and map related tenant fields
            if (userFromRequest.getTenant() == null) {
                log.error("User tenant is null for userId={}", userFromRequest.getPkUserId());
                throw new ResourceNotFoundException("Tenant information missing for user");
            }

            response.setTenantId(userFromRequest.getTenant().getTenantID());
            response.setFullName(userFromRequest.getFirstName() + " " + userFromRequest.getLastName());
            response.setMobilePhone(userFromRequest.getPhoneNo());
            response.setMappedTenant(new TenantLean(userFromRequest.getTenant().getTenantID(), userFromRequest.getTenant().getTenantName()));
            response.setMappedScopes(
                Optional.ofNullable(userFromRequest.getMappedGroups())
                        .orElse(Collections.emptySet())
                        .stream()
                        .flatMap(g -> Optional.ofNullable(g.getMappedRoles()).orElse(Collections.emptySet()).stream()
                                .flatMap(r -> Optional.ofNullable(r.getScopes()).orElse(Collections.emptySet()).stream())
                        )
                        .map(Scopes::getScopeName)
                        .collect(Collectors.toSet())
            );
            log.info("login - completed for userId={}", userFromRequest.getPkUserId());
            return response;

        } catch (ResourceNotFoundException rnfe) {
            // Known error conditions are logged above; rethrow for controller handling
            throw rnfe;
        } catch (Exception e) {
            // Unexpected exceptions should be logged with stacktrace for diagnostics
            log.error("Unexpected error in login: {}", e.getMessage(), e);
            throw e;
        }
    }
}