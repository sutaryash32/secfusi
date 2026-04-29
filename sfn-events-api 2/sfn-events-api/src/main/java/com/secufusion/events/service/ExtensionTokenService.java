package com.secufusion.events.service;

import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ExtensionTokenService
 *
 * Service for generating JWT tokens using API keys and Keycloak integration.
 * Manages device user creation and group assignment during token generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionTokenService {

    private final ExtensionApiKeyService apiKeyService;
    private final ExtensionApiKeyConfigurationService configService;
    private final ExtensionApiKeyRepository apiKeyRepository;
    private final DeviceUserRepository deviceUserRepository;
    private final EventsGroupRepository eventsGroupRepository;
    private final EventsGroupDeviceUserMappingRepository groupMappingRepository;
    private final TenantRepository tenantRepository;
    private final KeycloakClientService keycloakClientService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.admin.server-url}")
    private String keycloakServerUrl;

    /**
     * Validate API key (public endpoint)
     */
    @Transactional
    public ValidateExtensionApiKeyResponse validateApiKey(String rawKey) {
        log.info("[EXTENSION-TOKEN] Validating API key");
        return apiKeyService.validateApiKeyInternal(rawKey);
    }

    /**
     * Generate JWT token using API key
     */
    @Transactional
    public GenerateTokenResponse generateToken(GenerateTokenRequest request) {
        log.info("[EXTENSION-TOKEN] Generating token for email={}", request.getDeviceUserDetails().getEmail());

        // Step 1: Validate API key
        ValidateExtensionApiKeyResponse validation = apiKeyService.validateApiKeyInternal(request.getApiKey());
        if (!validation.getValid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Invalid API key: " + validation.getErrorMessage());
        }

        // Get API key entity
        ExtensionApiKey apiKey = apiKeyRepository.findByKeyHash(
            com.secufusion.events.util.ApiKeyUtil.hash(request.getApiKey())
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key not found"));

        // Step 2: Create or update device user
        DeviceUser deviceUser = createOrUpdateDeviceUser(
            apiKey.getTenantId(),
            request.getDeviceUserDetails()
        );

        // Step 3: Assign to default API key group
        assignToDefaultGroup(apiKey.getTenantId(), deviceUser);

        // Step 4: Generate Keycloak token
        String accessToken = generateKeycloakToken(apiKey, deviceUser);

        // Step 5: Build response with warnings
        String warningMessage = buildWarningMessage(apiKey);

        return GenerateTokenResponse.builder()
            .accessToken(accessToken)
            .tokenType("Bearer")
            .expiresIn(3600L) // Default 1 hour
            .tokenExpiresAt(LocalDateTime.now().plusHours(1))
            .tenantId(apiKey.getTenantId())
            .apiKeyExpiresAt(apiKey.getExpiresAt())
            .apiKeyDaysRemaining(apiKey.getDaysRemaining())
            .message("Token generated successfully")
            .warningMessage(warningMessage)
            .build();
    }

    /**
     * Generate token with validation steps (detailed response)
     */
    @Transactional
    public TokenValidationStepResponse generateTokenWithSteps(GenerateTokenRequest request) {
        log.info("[EXTENSION-TOKEN] Generating token with steps for email={}",
            request.getDeviceUserDetails().getEmail());

        TokenValidationStepResponse response = new TokenValidationStepResponse();

        // Step 1: Validate API key
        TokenValidationStepResponse.StepResult step1 = new TokenValidationStepResponse.StepResult();
        try {
            ValidateExtensionApiKeyResponse validation = apiKeyService.validateApiKeyInternal(request.getApiKey());
            if (validation.getValid()) {
                step1.setSuccess(true);
                step1.setMessage("API key is valid");
            } else {
                step1.setSuccess(false);
                step1.setError(validation.getErrorMessage());
                response.setStep1ValidateApiKey(step1);
                response.setSuccess(false);
                response.setErrorMessage("API key validation failed");
                return response;
            }
        } catch (Exception e) {
            step1.setSuccess(false);
            step1.setError(e.getMessage());
            response.setStep1ValidateApiKey(step1);
            response.setSuccess(false);
            response.setErrorMessage("API key validation failed");
            return response;
        }
        response.setStep1ValidateApiKey(step1);

        // Step 2: Check expiry
        TokenValidationStepResponse.StepResult step2 = new TokenValidationStepResponse.StepResult();
        try {
            ExtensionApiKey apiKey = apiKeyRepository.findByKeyHash(
                com.secufusion.events.util.ApiKeyUtil.hash(request.getApiKey())
            ).orElseThrow();

            if (apiKey.isExpired()) {
                step2.setSuccess(false);
                step2.setError("API key has expired");
                response.setStep2CheckExpiry(step2);
                response.setSuccess(false);
                response.setErrorMessage("API key has expired");
                return response;
            }

            int warningDays = configService.getExpiryWarningThresholdDays();
            if (apiKey.isExpiringSoon(warningDays)) {
                step2.setSuccess(true);
                step2.setMessage("API key expires in " + apiKey.getDaysRemaining() + " days");
            } else {
                step2.setSuccess(true);
                step2.setMessage("API key is valid and not expiring soon");
            }
        } catch (Exception e) {
            step2.setSuccess(false);
            step2.setError(e.getMessage());
            response.setStep2CheckExpiry(step2);
            response.setSuccess(false);
            response.setErrorMessage("Expiry check failed");
            return response;
        }
        response.setStep2CheckExpiry(step2);

        // Step 3: Generate token
        TokenValidationStepResponse.StepResult step3 = new TokenValidationStepResponse.StepResult();
        try {
            GenerateTokenResponse tokenResponse = generateToken(request);
            step3.setSuccess(true);
            step3.setMessage("Token generated successfully");
            response.setStep3GenerateToken(step3);
            response.setTokenData(tokenResponse);
            response.setSuccess(true);
        } catch (Exception e) {
            step3.setSuccess(false);
            step3.setError(e.getMessage());
            response.setStep3GenerateToken(step3);
            response.setSuccess(false);
            response.setErrorMessage("Token generation failed: " + e.getMessage());
        }

        return response;
    }

    /**
     * Check API key expiry
     */
    @Transactional
    public ApiKeyExpiryCheckResponse checkExpiry(String rawKey) {
        log.info("[EXTENSION-TOKEN] Checking API key expiry");

        ValidateExtensionApiKeyResponse validation = apiKeyService.validateApiKeyInternal(rawKey);

        if (!validation.getValid()) {
            return ApiKeyExpiryCheckResponse.builder()
                .valid(false)
                .build();
        }

        ExtensionApiKey apiKey = apiKeyRepository.findByKeyHash(
            com.secufusion.events.util.ApiKeyUtil.hash(rawKey)
        ).orElseThrow();

        int warningDays = configService.getExpiryWarningThresholdDays();
        String warningMessage = buildWarningMessage(apiKey);

        return ApiKeyExpiryCheckResponse.builder()
            .valid(true)
            .keyId(apiKey.getPkExtensionApiKeyId())
            .status(apiKey.getStatus())
            .expiresAt(apiKey.getExpiresAt())
            .daysRemaining(apiKey.getDaysRemaining())
            .expiringSoon(apiKey.isExpiringSoon(warningDays))
            .warningMessage(warningMessage)
            .build();
    }

    /**
     * Get usage statistics for an API key
     */
    @Transactional(readOnly = true)
    public ApiKeyUsageStatsResponse getUsageStats(String rawKey) {
        log.info("[EXTENSION-TOKEN] Getting usage stats for API key");

        ValidateExtensionApiKeyResponse validation = apiKeyService.validateApiKeyInternal(rawKey);
        if (!validation.getValid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
        }

        ExtensionApiKey apiKey = apiKeyRepository.findByKeyHash(
            com.secufusion.events.util.ApiKeyUtil.hash(rawKey)
        ).orElseThrow();

        // Get device users for this tenant (simplified - in production, track API key usage)
        List<DeviceUser> deviceUsers = deviceUserRepository.findByTenantIdAndStatus(
            apiKey.getTenantId(), "ACTIVE");

        List<ApiKeyUsageStatsResponse.DeviceUserStats> userStats = new ArrayList<>();
        for (DeviceUser user : deviceUsers) {
            long groupCount = groupMappingRepository.findActiveGroupsForDeviceUser(
                user.getPkDeviceUserId(), apiKey.getTenantId()
            ).size();

            userStats.add(ApiKeyUsageStatsResponse.DeviceUserStats.builder()
                .deviceUserId(user.getPkDeviceUserId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .displayName(user.getDisplayName())
                .status(user.getStatus())
                .groupCount((int) groupCount)
                .lastSeenAt(user.getLastSeenAt())
                .build());
        }

        return ApiKeyUsageStatsResponse.builder()
            .apiKeyId(apiKey.getPkExtensionApiKeyId())
            .tenantId(apiKey.getTenantId())
            .totalUsers(userStats.size())
            .activeUsers((int) userStats.stream().filter(u -> "ACTIVE".equals(u.getStatus())).count())
            .users(userStats)
            .build();
    }

    /**
     * Create or update device user
     */
    private DeviceUser createOrUpdateDeviceUser(String tenantId, GenerateTokenRequest.DeviceUserDetails details) {
        Optional<DeviceUser> existingUser = deviceUserRepository.findByTenantIdAndEmail(tenantId, details.getEmail());

        if (existingUser.isPresent()) {
            DeviceUser user = existingUser.get();
            user.setLastSeenAt(LocalDateTime.now());
            user.setUserName(details.getUserName() != null ? details.getUserName() : user.getUserName());
            user.setDisplayName(details.getDisplayName() != null ? details.getDisplayName() : user.getDisplayName());
            user.setPortalUserId(details.getPortalUserId() != null ? details.getPortalUserId() : user.getPortalUserId());
            return deviceUserRepository.save(user);
        } else {
            DeviceUser newUser = DeviceUser.builder()
                .tenantId(tenantId)
                .email(details.getEmail())
                .userName(details.getUserName() != null ? details.getUserName() : details.getEmail())
                .displayName(details.getDisplayName())
                .portalUserId(details.getPortalUserId())
                .source(details.getSource())
                .status("ACTIVE")
                .firstSeenAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .build();

            log.info("[EXTENSION-TOKEN] Creating new device user: email={}", details.getEmail());
            return deviceUserRepository.save(newUser);
        }
    }

    /**
     * Assign device user to default API key group
     */
    private void assignToDefaultGroup(String tenantId, DeviceUser deviceUser) {
        Optional<EventsGroup> defaultGroup = eventsGroupRepository.findByTenantIdAndIsDefaultTrue(tenantId);

        if (defaultGroup.isEmpty()) {
            log.warn("[EXTENSION-TOKEN] No default group found for tenant={}", tenantId);
            return;
        }

        EventsGroup group = defaultGroup.get();
        boolean alreadyAssigned = groupMappingRepository.existsByFkDeviceUserIdAndFkEventsGroupId(
            deviceUser.getPkDeviceUserId(), group.getPkEventsGroupId()
        );

        if (!alreadyAssigned) {
            EventsGroupDeviceUserMapping mapping = EventsGroupDeviceUserMapping.builder()
                .pkMappingId(UUID.randomUUID().toString())
                .fkDeviceUserId(deviceUser.getPkDeviceUserId())
                .fkEventsGroupId(group.getPkEventsGroupId())
                .assignedAt(LocalDateTime.now())
                .assignedBy("SYSTEM_API_KEY")
                .build();

            groupMappingRepository.save(mapping);
            log.info("[EXTENSION-TOKEN] Assigned device user to default group: email={}, group={}",
                deviceUser.getEmail(), group.getName());
        }
    }

    /**
     * Generate Keycloak JWT token using client_credentials grant
     *
     * Note: API keys are custom auth. Keycloak only sees client_credentials.
     * All API keys for a tenant use the SAME Keycloak client credentials.
     */
    private String generateKeycloakToken(ExtensionApiKey apiKey, DeviceUser deviceUser) {
        try {
            // Get tenant for realm name
            Tenant tenant = tenantRepository.findById(apiKey.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

            // Decrypt client secret
            String decryptedSecret = keycloakClientService.decryptSecret(apiKey.getClientSecret());

            // Build Keycloak token URL
            String tokenUrl = keycloakServerUrl + "/realms/" + tenant.getRealmName() +
                "/protocol/openid-connect/token";

            // Prepare client_credentials request
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", apiKey.getClientId());
            body.add("client_secret", decryptedSecret);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Call Keycloak
            log.debug("[EXTENSION-TOKEN] Calling Keycloak: realm={}, clientId={}",
                tenant.getRealmName(), apiKey.getClientId());

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve access token from Keycloak");
            }

            String accessToken = (String) response.getBody().get("access_token");
            Integer expiresIn = (Integer) response.getBody().get("expires_in");

            log.info("[EXTENSION-TOKEN] Token generated successfully: user={}, expiresIn={}s",
                deviceUser.getEmail(), expiresIn);

            return accessToken;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("[EXTENSION-TOKEN] Failed to generate Keycloak token: clientId={}",
                apiKey.getClientId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Failed to generate token from Keycloak: " + e.getMessage());
        }
    }

    /**
     * Build warning message for expiring keys
     */
    private String buildWarningMessage(ExtensionApiKey apiKey) {
        int warningDays = configService.getExpiryWarningThresholdDays();

        if (apiKey.isExpiringSoon(warningDays)) {
            return "WARNING: API key expires in " + apiKey.getDaysRemaining() + " days. Please rotate or extend.";
        }

        return null;
    }
}
