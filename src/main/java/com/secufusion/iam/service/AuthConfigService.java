package com.secufusion.iam.service;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.dto.DeviceInfoRequest;
import com.secufusion.iam.dto.LoginResponseDto;
import com.secufusion.iam.dto.SsoLoginResponseDto;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.AccessDeniedException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.AuthProviderConfigRepository;
import com.secufusion.iam.repository.SsoConfigurationRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${azure.mismatch.validation:FALSE}")
    private Boolean azureMismatchValidationMode;

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
     * entity into a LoginResponseDto (without device info).
     *
     * @param request incoming HTTP request
     * @param token   bearer token (raw)
     * @return LoginResponseDto populated from resolved user
     * @throws ResourceNotFoundException when token validation fails or required data is missing
     */
    public LoginResponseDto login(HttpServletRequest request, String token) {
        return login(request, token, null);
    }

    /**
     * Handle login by validating the token against the request and mapping the resolved User
     * entity into a LoginResponseDto.
     * <p>
     * Extensive logging is performed for request validation, mapping steps and exception cases.
     *
     * @param request    incoming HTTP request
     * @param token      bearer token (raw)
     * @param deviceInfo device information from frontend (optional)
     * @return LoginResponseDto populated from resolved user
     * @throws ResourceNotFoundException when token validation fails or required data is missing
     */
    public LoginResponseDto login(HttpServletRequest request, String token, DeviceInfoRequest deviceInfo) {
        log.info("login - start");
        log.debug("login - request remoteAddr={}, tokenPresent={}, deviceInfoPresent={}",
                request != null ? request.getRemoteAddr() : "null",
                token != null,
                deviceInfo != null && deviceInfo.getDeviceFingerprint() != null);

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
            String azureTenantId = jwtUtil.getAzureTenantIdFromToken(token);

            if(azureMismatchValidationMode) {
                if (azureTenantId != null && !azureTenantId.isBlank()) {

                    if (!userFromRequest.isDefaultUser() && tenantFromRequest.getAzureTenantId() == null) {

                        log.warn(
                                "Normal user '{}' attempted login before tenant '{}' was initialized by default user",
                                userFromRequest.getUserName(),
                                tenantFromRequest.getTenantID()
                        );

                        throw new AccessDeniedException(
                                "Tenant is not initialized. Please ask the tenant administrator to log in first."
                        );
                    }

                    // ✅ FIRST LOGIN — default user bootstraps tenant
                    if (userFromRequest.isDefaultUser() && tenantFromRequest.getAzureTenantId() == null) {

                        log.info(
                                "Binding Azure tenantId '{}' to tenant '{}' (default user bootstrap login)",
                                azureTenantId,
                                tenantFromRequest.getTenantID()
                        );

                        tenantFromRequest.setAzureTenantId(azureTenantId);
                        tenantRepository.save(tenantFromRequest);
                    } else if (tenantFromRequest.getAzureTenantId() != null &&
                            !tenantFromRequest.getAzureTenantId().equalsIgnoreCase(azureTenantId)) {

                        log.error(
                                "Azure tenant mismatch. DB='{}' TOKEN='{}' tenant='{}'",
                                tenantFromRequest.getAzureTenantId(),
                                azureTenantId,
                                tenantFromRequest.getTenantID()
                        );

                        throw new AccessDeniedException(
                                "IDP mismatch – access denied"
                        );
                    }
                } else {
                    // Non-Azure / other IDP login
                    log.debug(
                            "Login without Azure tenantId for tenant '{}'. Skipping Azure validation.",
                            tenantFromRequest.getTenantID()
                    );
                }
            }

            if (azureMismatchValidationMode){
                applyAzureTenantToggleValidation(tenantFromRequest, userFromRequest, azureTenantId);
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

            // Log successful login event with device info if available
            try {
                if (deviceInfo != null && deviceInfo.getDeviceFingerprint() != null) {
                    // Log with device tracking
                    loginAuditService.logLoginWithDevice(
                            tenantFromRequest.getTenantID(),
                            tenantFromRequest.getRealmName(),
                            userFromRequest.getPkUserId(),
                            userFromRequest.getUserName(),
                            userFromRequest.getEmail(),
                            request.getRemoteAddr(),
                            request.getHeader("User-Agent"),
                            null, // sessionId
                            null, // clientId
                            "SSO", // authMethod
                            deviceInfo.getDeviceId(),
                            deviceInfo.getDeviceFingerprint(),
                            deviceInfo.getDeviceName(),
                            deviceInfo.getBrowserType(),
                            deviceInfo.getOsInfo(),
                            true, // success
                            null, // errorMessage
                            null  // errorCode
                    );
                    log.info("Logged login with device tracking for user={}, fingerprint={}",
                            userFromRequest.getUserName(), deviceInfo.getDeviceFingerprint());
                } else {
                    // Log without device tracking (backward compatible)
                    loginAuditService.logLoginSuccess(
                            tenantFromRequest.getTenantID(),
                            tenantFromRequest.getRealmName(),
                            userFromRequest.getPkUserId(),
                            userFromRequest.getUserName(),
                            userFromRequest.getEmail(),
                            request.getRemoteAddr(),
                            request.getHeader("User-Agent"),
                            null, // sessionId
                            null, // clientId
                            "TOKEN" // authMethod
                    );
                }
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

    private void applyAzureTenantToggleValidation(
            Tenant tenant,
            User user,
            String tokenAzureTenantId) {

        // No Azure in token → nothing to validate
        if (tokenAzureTenantId == null || tokenAzureTenantId.isBlank()) {
            return;
        }

        // Tenant not Azure-enabled → skip
        if (tenant.getAzureTenantId() == null || tenant.getAzureTenantId().isBlank()) {
            return;
        }

        // 🔒 STRICT MODE ENFORCEMENT
        if (!tenant.getAzureTenantId().equalsIgnoreCase(tokenAzureTenantId)) {

            log.error(
                    "Strict Azure tenant validation failed. user='{}' dbTenant='{}' tokenTenant='{}'",
                    user.getUserName(),
                    tenant.getAzureTenantId(),
                    tokenAzureTenantId
            );

            throw new AccessDeniedException("Azure tenant mismatch");
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
                return unauthorized("Invalid request");
            }

            // Validate token against request header
            if (!jwtUtil.validateRequestToken(request, token)) {
                log.warn("ssoLogin: Token validation failed");
                return unauthorized("Invalid or missing token");
            }

            // Extract Azure tenant ID from token
            String azureTenantId = jwtUtil.getAzureTenantIdFromToken(token);
            if (azureTenantId == null || azureTenantId.isBlank()) {
                log.warn("ssoLogin: azure_tenant_id missing in token");
                return unauthorized("Unauthorized - Azure tenant ID missing");
            }

            // Resolve tenant strictly by azureTenantId
            Optional<Tenant> tenantOpt =
                    tenantRepository.findByAzureTenantId(azureTenantId);

            if (tenantOpt.isEmpty()) {
                log.warn("ssoLogin: No tenant mapped to azureTenantId={}", azureTenantId);
                return unauthorized("Unauthorized - Tenant not registered");
            }

            Tenant tenant = tenantOpt.get();

            // 🔐 FINAL tenant mismatch guard (defensive)
            if (tenant.getAzureTenantId() == null ||
                    !tenant.getAzureTenantId().equalsIgnoreCase(azureTenantId)) {

                log.error(
                        "ssoLogin: Azure tenant mismatch. DB='{}' TOKEN='{}'",
                        tenant.getAzureTenantId(),
                        azureTenantId
                );

                return unauthorized("Unauthorized - Tenant mismatch");
            }

            // Extract claims for response
            String username = jwtUtil.getUsername(request);
            String preferredUsername = jwtUtil.getPreferredUsernameFromRequest(request);

            String alias = null;

            // SSO config OPTIONAL if tenant is Azure-bound
            Optional<SsoConfiguration> ssoConfigOpt =
                    ssoConfigurationRepository.findByFkTenantIdAndActive(
                            tenant.getTenantID(), "ACTIVE");

            if (ssoConfigOpt.isPresent()) {
                SsoConfiguration ssoConfig = ssoConfigOpt.get();

                if (Boolean.TRUE.equals(ssoConfig.getEnabled())) {
                    alias = ssoConfig.getAlias();
                } else {
                    log.warn("ssoLogin: SSO disabled for tenant={}", tenant.getTenantID());
                    return unauthorized("Unauthorized - SSO disabled for tenant");
                }
            }

            // ✅ SUCCESS
            log.info(
                    "ssoLogin: Success for tenant='{}' user='{}'",
                    tenant.getTenantName(),
                    preferredUsername
            );

            return SsoLoginResponseDto.builder()
                    .authorized(true)
                    .message("SSO authentication successful")
                    .username(username)
                    .preferredUsername(preferredUsername)
                    .tenantName(tenant.getTenantName())
                    .alias(alias)
                    .build();

        } catch (Exception e) {
            log.error("ssoLogin: Unexpected error", e);
            return unauthorized("Unauthorized");
        }
    }

    private SsoLoginResponseDto unauthorized(String message) {
        return SsoLoginResponseDto.builder()
                .authorized(false)
                .message(message)
                .build();
    }


}