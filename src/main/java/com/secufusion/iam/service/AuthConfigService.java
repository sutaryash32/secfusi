package com.secufusion.iam.service;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.dto.LoginResponseDto;
import com.secufusion.iam.dto.SsoLoginResponseDto;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AuthProviderConfigRepository;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.*;
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

    @Autowired
    private JwtUtl jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAuditService loginAuditService;

    @Autowired
    private SsoConfigurationRepository ssoConfigurationRepository;

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
            if (request == null) {
                log.error("HttpServletRequest is null");
                throw new ResourceNotFoundException("Invalid request");
            }

            // Validate token against request
            log.debug("Validating request token");
            if (!jwtUtil.validateRequestToken(request, token)) {
                log.warn("Token validation failed for request from {}", request.getRemoteAddr());
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

            // Resolve tenant from the request using jwtUtil and validate it matches the token/user tenant
            log.debug("Resolving tenant from request via jwtUtil");
            Tenant tenantFromRequest = jwtUtil.getTenantFromRequest(request);
            if (tenantFromRequest == null) {
                log.error("Tenant extraction returned null from request for userId={}", userFromRequest.getPkUserId());
                throw new ResourceNotFoundException("Tenant not found in token/request");
            }
            if (userFromRequest.getTenant() == null) {
                log.error("User tenant is null for userId={}", userFromRequest.getPkUserId());
                throw new ResourceNotFoundException("Tenant information missing for user");
            }

            // Compare tenant identity (prefer comparing tenant ID if available)
            boolean tenantMatches = Objects.equals(tenantFromRequest.getTenantID(), userFromRequest.getTenant().getTenantID())
                    || Objects.equals(tenantFromRequest.getTenantName(), userFromRequest.getTenant().getTenantName());
            if (!tenantMatches) {
                log.warn("Tenant mismatch: requestTenant={} tokenTenant={} for userId={}",
                        tenantFromRequest.getTenantName(),
                        userFromRequest.getTenant().getTenantName(),
                        userFromRequest.getPkUserId());
                throw new ResourceNotFoundException("Tenant mismatch between request and token");
            }

            // Map user to response DTO
            LoginResponseDto response = new LoginResponseDto();
            response.setUserId(userFromRequest.getPkUserId());
            response.setUsername(userFromRequest.getUserName());
            response.setEmail(userFromRequest.getEmail());
            response.setFirstName(userFromRequest.getFirstName());
            response.setLastName(userFromRequest.getLastName());
            response.setAccessToken(token); // Do not log token content
            String userTenantType =
                    Optional.ofNullable(userFromRequest.getTenant().getTenantType())
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException("User tenant type is missing"));

            response.setUserType(userTenantType);

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

            response.setTenantId(userFromRequest.getTenant().getTenantID());
            response.setFullName(userFromRequest.getFirstName() + " " + userFromRequest.getLastName());
            response.setMobilePhone(userFromRequest.getPhoneNo());
            response.setMappedTenant(new TenantLean(userFromRequest.getTenant().getTenantID(), userFromRequest.getTenant().getTenantName()));
            Map<String, Map<String, Set<String>>> permissionMatrix =
                    userFromRequest.getMappedGroups()
                            .stream()
                            .flatMap(group -> group.getMappedRoles().stream())
                            .flatMap(role -> role.getScopes().stream())
                            .filter(scope ->
                                    scope.getTenantTypes() != null &&
                                            scope.getTenantTypes().stream().anyMatch(tt ->
                                                    tt.getTenantTypeName() != null &&
                                                            tt.getTenantTypeName().trim().equalsIgnoreCase(userTenantType)
                                            )
                            )
                            .collect(Collectors.groupingBy(
                                    Scopes::getMenuName,
                                    Collectors.groupingBy(
                                            Scopes::getSubMenu,
                                            Collectors.mapping(
                                                    Scopes::getAction,
                                                    Collectors.toSet()
                                            )
                                    )
                            ));
            response.setPermissionMatrix(permissionMatrix);

            // Log successful login event
            try {
                loginAuditService.logLoginSuccess(
                        tenantFromRequest.getTenantID(),
                        tenantFromRequest.getRealmName(),
                        userFromRequest.getPkUserId(),
                        userFromRequest.getUserName(),
                        userFromRequest.getEmail(),
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent"),
                        null, // sessionId - can be extracted from token if available
                        null, // clientId - can be extracted from token if available
                        "TOKEN" // authMethod
                );
            } catch (Exception auditEx) {
                log.warn("Failed to log login audit event: {}", auditEx.getMessage());
            }

            log.info("login - completed for userId={}", userFromRequest.getPkUserId());
            return response;

        } catch (ResourceNotFoundException rnfe) {
            // Log failed login event
            logLoginFailureEvent(request, null, rnfe.getMessage(), "RESOURCE_NOT_FOUND");
            throw rnfe;
        } catch (Exception e) {
            // Log failed login event
            logLoginFailureEvent(request, null, e.getMessage(), "UNEXPECTED_ERROR");
            log.error("Unexpected error in login: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Helper method to log login failure events.
     */
    private void logLoginFailureEvent(HttpServletRequest request, String tenantId, String errorMessage, String errorCode) {
        try {
            String ipAddress = request != null ? request.getRemoteAddr() : null;
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            loginAuditService.logLoginFailure(
                    tenantId,
                    null, // realmName
                    null, // username - not known at failure time
                    null, // email - not known at failure time
                    ipAddress,
                    userAgent,
                    errorMessage,
                    errorCode
            );
        } catch (Exception auditEx) {
            log.warn("Failed to log login failure audit event: {}", auditEx.getMessage());
        }
    }

    public LoginResponseDto loginByEmail(String email) {
        log.info("loginByEmail - start for email={}", email);

        try {
            if (email == null || email.isBlank()) {
                throw new ResourceNotFoundException("Email must not be null or empty");
            }

            // Fetch user by email (case-insensitive recommended)
            User user = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email));

            if (user.getTenant() == null) {
                throw new ResourceNotFoundException("Tenant information missing for user");
            }


            Tenant tenant = user.getTenant();

            String tenantType =
                    Optional.ofNullable(tenant.getTenantType())
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException("Tenant type not found"));

            // -------------------------------
            // Build Login Response
            // -------------------------------
            LoginResponseDto response = new LoginResponseDto();
            response.setUserId(user.getPkUserId());
            response.setUsername(user.getUserName());
            response.setEmail(user.getEmail());
            response.setFirstName(user.getFirstName());
            response.setLastName(user.getLastName());
            response.setFullName(user.getFirstName() + " " + user.getLastName());
            response.setUserType(tenantType);
            response.setTenantId(tenant.getTenantID());
            response.setMappedTenant(new TenantLean(
                    tenant.getTenantID(),
                    tenant.getTenantName()
            ));

            // -------------------------------
            // Groups → GroupsLean
            // -------------------------------
            Set<GroupsLean> mappedGroups =
                    Optional.ofNullable(user.getMappedGroups())
                            .orElse(Collections.emptySet())
                            .stream()
                            .map(group -> {

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

                                return new GroupsLean(
                                        group.getPkGroupId(),
                                        group.getName(),
                                        roles
                                );
                            })
                            .collect(Collectors.toSet());

            response.setMappedGroups(mappedGroups);

            // -------------------------------
// 🔥 Permission Matrix (Tenant-Type Filtered)
// -------------------------------
            Map<String, Map<String, Set<String>>> permissionMatrix =
                    user.getMappedGroups()
                            .stream()
                            .flatMap(group -> group.getMappedRoles().stream())
                            .flatMap(role -> role.getScopes().stream())
                            .filter(scope ->
                                    scope.getTenantTypes() != null &&
                                            scope.getTenantTypes().stream().anyMatch(tt ->
                                                    tt.getTenantTypeName() != null &&
                                                            tt.getTenantTypeName().trim().equalsIgnoreCase(tenantType)
                                            )
                            )
                            .collect(Collectors.groupingBy(
                                    Scopes::getMenuName,
                                    Collectors.groupingBy(
                                            Scopes::getSubMenu,
                                            Collectors.mapping(
                                                    Scopes::getAction,
                                                    Collectors.toSet()
                                            )
                                    )
                            ));

            response.setPermissionMatrix(permissionMatrix);


            log.info("loginByEmail - completed for userId={}", user.getPkUserId());
            return response;

        } catch (Exception e) {
            log.error("Unexpected error in loginByEmail for email={}", email, e);
            throw e;
        }
    }

    /**
     * Handle SSO login by validating the Azure tenant ID from JWT token
     * against registered SSO configurations.
     *
     * @param request incoming HTTP request
     * @param token   bearer token (raw)
     * @return SsoLoginResponseDto with authorization status
     * @throws ResourceNotFoundException when SSO configuration is not found or disabled
     */
    public SsoLoginResponseDto ssoLogin(HttpServletRequest request, String token) {
        log.info("ssoLogin - start");

        try {
            if (request == null) {
                log.error("ssoLogin: HttpServletRequest is null");
                throw new ResourceNotFoundException("Invalid request");
            }

            // Validate token against request header
            log.debug("ssoLogin: Validating request token");
            if (!jwtUtil.validateRequestToken(request, token)) {
                log.warn("ssoLogin: Token validation failed for request from {}", request.getRemoteAddr());
                throw new ResourceNotFoundException("Invalid or missing token");
            }
            log.info("ssoLogin: Token validated successfully");

            // Extract Azure tenant ID from token
            String azureTenantId = jwtUtil.getAzureTenantIdFromToken(token);
            if (azureTenantId == null || azureTenantId.isBlank()) {
                log.warn("ssoLogin: azure_tenant_id claim is missing or empty in token");
                throw new ResourceNotFoundException("Azure tenant ID not found in token");
            }
            log.info("ssoLogin: Extracted azure_tenant_id={}", azureTenantId);

            // Look up SSO configuration by tenantId (Azure tenant ID)
            List<SsoConfiguration> ssoConfigurations = ssoConfigurationRepository.findByTenantId(azureTenantId);

            if (ssoConfigurations == null || ssoConfigurations.isEmpty()) {
                log.warn("ssoLogin: No SSO configuration found for azure_tenant_id={}", azureTenantId);
                throw new ResourceNotFoundException(
                        "SSO configuration not registered for Azure tenant: " + azureTenantId
                );
            }

            // Get the first matching configuration
            SsoConfiguration ssoConfig = ssoConfigurations.get(0);
            log.debug("ssoLogin: Found SSO configuration id={} alias={}", ssoConfig.getId(), ssoConfig.getAlias());

            // Check if SSO is enabled
            if (ssoConfig.getEnabled() == null || !ssoConfig.getEnabled()) {
                log.warn("ssoLogin: SSO is disabled for configuration id={}", ssoConfig.getId());
                throw new ResourceNotFoundException("SSO is not enabled for this tenant");
            }

            // Extract claims for response
            String username = jwtUtil.getUsername(request);
            String preferredUsername = jwtUtil.getPreferredUsernameFromRequest(request);

            // Get tenant name from fkTenantId
            String tenantName = null;
            if (ssoConfig.getFkTenantId() != null) {
                tenantName = tenantRepository.findById(ssoConfig.getFkTenantId())
                        .map(Tenant::getTenantName)
                        .orElse(null);
            }

            // Build successful response
            SsoLoginResponseDto response = SsoLoginResponseDto.builder()
                    .authorized(true)
                    .message("SSO authentication successful")
                    .username(username)
                    .preferredUsername(preferredUsername)
                    .tenantName(tenantName)
                    .alias(ssoConfig.getAlias())
                    .build();

            log.info("ssoLogin: Completed successfully for azure_tenant_id={}, user={}",
                    azureTenantId, preferredUsername);

            return response;

        } catch (ResourceNotFoundException rnfe) {
            log.warn("ssoLogin: Resource not found - {}", rnfe.getMessage());
            throw rnfe;
        } catch (Exception e) {
            log.error("ssoLogin: Unexpected error - {}", e.getMessage(), e);
            throw e;
        }
    }

}