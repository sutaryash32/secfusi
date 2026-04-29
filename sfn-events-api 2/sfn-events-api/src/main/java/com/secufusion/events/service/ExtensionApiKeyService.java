package com.secufusion.events.service;

import com.secufusion.events.config.KeycloakAdminConfig;
import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.repository.*;
import com.secufusion.events.util.ApiKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.ClientRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ExtensionApiKeyService
 *
 * Core service for Extension API Key lifecycle management.
 * Handles creation, rotation, revocation, expiry, and validation.
 *
 * Note: API keys are a custom authentication layer. The underlying Keycloak
 * client_credentials remain the same for all keys in a tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionApiKeyService {

    private final ExtensionApiKeyRepository apiKeyRepository;
    private final ExtensionApiKeyRotationHistoryRepository rotationHistoryRepository;
    private final ExtensionApiKeyConfigurationService configService;
    private final TenantRepository tenantRepository;
    private final KeycloakClientService keycloakClientService; // Your existing Keycloak utility

    /**
     * Create a new API key
     *
     * Strategy: Multiple API keys share the same Keycloak client credentials.
     * API keys are a custom auth layer - Keycloak only sees client_credentials grants.
     *
     * @param request CreateExtensionApiKeyRequest containing only name and description
     * @param tenantId Tenant ID auto-resolved from JWT token via HttpServletRequest
     * @param userEmail User email auto-resolved from JWT token via HttpServletRequest
     */
    @Transactional
    public ExtensionApiKeyCreationResponse createApiKey(
        CreateExtensionApiKeyRequest request,
        String tenantId,
        String userEmail
    ) {
        log.info("[API-KEY] Creating API key for tenant={}, name={}, user={}",
            tenantId, request.getName(), userEmail);

        // Validate tenant exists
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        // Check max keys limit
        Integer maxKeys = configService.getMaxKeysPerTenant();
        if (maxKeys > 0) {
            long activeKeyCount = apiKeyRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
            if (activeKeyCount >= maxKeys) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Maximum active keys limit reached: " + maxKeys);
            }
        }

        // Generate raw API key (64 characters)
        String rawKey = ApiKeyUtil.generateApiKey(64);
        String keyHash = ApiKeyUtil.hash(rawKey);
        String keyPrefix = ApiKeyUtil.extractPrefix(rawKey);

        // Get or create Keycloak client for this tenant
        String clientId = tenant.getTenantName()
            .toLowerCase()
            .replaceAll("\\s+", "-") + "-extension-client";

        String encryptedSecret = getOrCreateKeycloakClient(tenant, clientId);

        // Auto-set expiry date to DEFAULT_EXPIRY_DAYS from now
        int defaultExpiryDays = configService.getDefaultExpiryDays();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(defaultExpiryDays);

        // Create API key entity
        ExtensionApiKey apiKey = ExtensionApiKey.builder()
            .tenantId(tenantId)
            .keyHash(keyHash)
            .keyPrefix(keyPrefix)
            .clientId(clientId)
            .clientSecret(encryptedSecret) // Same for all keys in tenant
            .name(request.getName())
            .description(request.getDescription())
            .status("ACTIVE")
            .expiresAt(expiresAt)
            .createdBy(userEmail)  // From JWT token
            .build();

        apiKey = apiKeyRepository.save(apiKey);

        log.info("[API-KEY] API key created successfully: keyId={}, prefix={}, clientId={}",
            apiKey.getPkExtensionApiKeyId(), keyPrefix, clientId);

        // Build response
        ExtensionApiKeyResponse keyResponse = mapToResponse(apiKey);
        return ExtensionApiKeyCreationResponse.builder()
            .key(keyResponse)
            .rawKey(rawKey)
            .warning("Store this API key securely. It will not be shown again.")
            .build();
    }

    /**
     * Get or create Keycloak client for tenant
     * Returns encrypted client secret
     */
    private String getOrCreateKeycloakClient(Tenant tenant, String clientId) {
        try {
            ClientRepresentation extensionClient;

            // Check if client exists
            if (keycloakClientService.clientExists(tenant.getRealmName(), clientId)) {
                log.debug("[API-KEY] Keycloak client exists: clientId={}", clientId);
                extensionClient = keycloakClientService.getClientWithSecret(
                    tenant.getRealmName(),
                    clientId
                );
            } else {
                log.info("[API-KEY] Creating new Keycloak client: clientId={}", clientId);

                // Create new client
                ClientRepresentation client = new ClientRepresentation();
                client.setClientId(clientId);
                client.setServiceAccountsEnabled(true);
                client.setPublicClient(false);
                client.setStandardFlowEnabled(false);
                client.setImplicitFlowEnabled(false);
                client.setDirectAccessGrantsEnabled(false);
                client.setRedirectUris(List.of(normalizeDomainForRedirect(tenant.getDomain())));

                keycloakClientService.createClient(tenant.getRealmName(), client);

                // Retrieve with secret
                extensionClient = keycloakClientService.getClientWithSecret(
                    tenant.getRealmName(),
                    clientId
                );
            }

            // Encrypt and return secret
            String encryptedSecret = keycloakClientService.encryptSecret(extensionClient.getSecret());
            log.debug("[API-KEY] Retrieved and encrypted Keycloak client secret");

            return encryptedSecret;

        } catch (Exception e) {
            log.error("[API-KEY] Failed to get/create Keycloak client: clientId={}", clientId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to setup Keycloak client: " + e.getMessage());
        }
    }

    /**
     * Normalize domain for redirect URI
     */
    private String normalizeDomainForRedirect(String domain) {
        if (domain == null || domain.isBlank()) {
            return "https://localhost/*";
        }
        if (!domain.startsWith("http")) {
            domain = "https://" + domain;
        }
        return domain + "/*";
    }

    /**
     * Get API key by ID
     */
    @Transactional(readOnly = true)
    public ExtensionApiKeyResponse getApiKey(String keyId) {
        ExtensionApiKey apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        return mapToResponse(apiKey);
    }

    /**
     * List API keys with pagination
     */
    @Transactional(readOnly = true)
    public Page<ExtensionApiKeyResponse> listApiKeys(String tenantId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<ExtensionApiKey> keys;
        if (status != null && !status.isBlank()) {
            keys = apiKeyRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable);
        } else {
            keys = apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }

        return keys.map(this::mapToResponse);
    }

    /**
     * Update API key
     */
    @Transactional
    public ExtensionApiKeyResponse updateApiKey(String keyId, UpdateExtensionApiKeyRequest request, String updatedBy) {
        log.info("[API-KEY] Updating API key: keyId={}", keyId);

        ExtensionApiKey apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        // Prevent updates to revoked keys
        if ("REVOKED".equals(apiKey.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot update revoked API key");
        }

        // Update fields
        if (request.getName() != null) {
            apiKey.setName(request.getName());
        }
        if (request.getDescription() != null) {
            apiKey.setDescription(request.getDescription());
        }
        if (request.getExpiresAt() != null) {
            apiKey.setExpiresAt(request.getExpiresAt());
        }
        if (request.getStatus() != null) {
            // Validate status transitions
            if ("EXPIRED".equals(request.getStatus()) || "REVOKED".equals(request.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot manually set status to EXPIRED or REVOKED");
            }
            apiKey.setStatus(request.getStatus());
        }

        apiKey.setUpdatedBy(updatedBy);
        apiKey = apiKeyRepository.save(apiKey);

        log.info("[API-KEY] API key updated successfully: keyId={}", keyId);
        return mapToResponse(apiKey);
    }

    /**
     * Revoke API key (permanent)
     */
    @Transactional
    public void revokeApiKey(String keyId, String revokedBy) {
        log.info("[API-KEY] Revoking API key: keyId={}", keyId);

        ExtensionApiKey apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        apiKey.setStatus("REVOKED");
        apiKey.setUpdatedBy(revokedBy);
        apiKeyRepository.save(apiKey);

        log.info("[API-KEY] API key revoked successfully: keyId={}", keyId);
    }

    /**
     * Reactivate API key
     */
    @Transactional
    public ExtensionApiKeyResponse reactivateApiKey(String keyId, String reactivatedBy) {
        log.info("[API-KEY] Reactivating API key: keyId={}", keyId);

        ExtensionApiKey apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        // Check if expired
        if (apiKey.isExpired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot reactivate expired API key. Extend expiry first.");
        }

        apiKey.setStatus("ACTIVE");
        apiKey.setUpdatedBy(reactivatedBy);
        apiKey = apiKeyRepository.save(apiKey);

        log.info("[API-KEY] API key reactivated successfully: keyId={}", keyId);
        return mapToResponse(apiKey);
    }

    /**
     * Delete API key (hard delete)
     */
    @Transactional
    public void deleteApiKey(String keyId) {
        log.info("[API-KEY] Deleting API key: keyId={}", keyId);

        if (!apiKeyRepository.existsById(keyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found");
        }

        apiKeyRepository.deleteById(keyId);
        log.info("[API-KEY] API key deleted successfully: keyId={}", keyId);
    }

    /**
     * Rotate API key
     *
     * Note: Rotation only changes the API key itself.
     * The underlying Keycloak client credentials remain the same.
     */
    @Transactional
    public ExtensionApiKeyCreationResponse rotateApiKey(String keyId, RotateExtensionApiKeyRequest request,
                                                         String rotatedBy, String rotationIp) {
        log.info("[API-KEY] Rotating API key: keyId={}, reason={}", keyId, request.getReason());

        ExtensionApiKey oldKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        // Revoke old key
        oldKey.setStatus("REVOKED");
        oldKey.setUpdatedBy(rotatedBy);
        apiKeyRepository.save(oldKey);

        // Generate new API key (but keep same Keycloak client credentials)
        String rawKey = ApiKeyUtil.generateApiKey(64);
        String keyHash = ApiKeyUtil.hash(rawKey);
        String keyPrefix = ApiKeyUtil.extractPrefix(rawKey);

        // Create new key with same Keycloak credentials
        ExtensionApiKey newKey = ExtensionApiKey.builder()
            .tenantId(oldKey.getTenantId())
            .keyHash(keyHash)
            .keyPrefix(keyPrefix)
            .clientId(oldKey.getClientId()) // SAME client ID
            .clientSecret(oldKey.getClientSecret()) // SAME client secret
            .name(oldKey.getName())
            .description(oldKey.getDescription())
            .status("ACTIVE")
            .expiresAt(LocalDateTime.now().plusDays(configService.getDefaultExpiryDays()))
            .createdBy(rotatedBy)
            .build();

        newKey = apiKeyRepository.save(newKey);

        log.info("[API-KEY] New API key generated: keyId={}, prefix={}",
            newKey.getPkExtensionApiKeyId(), keyPrefix);

        // Record rotation history
        ExtensionApiKeyRotationHistory history = ExtensionApiKeyRotationHistory.builder()
            .oldApiKeyId(oldKey.getPkExtensionApiKeyId())
            .newApiKeyId(newKey.getPkExtensionApiKeyId())
            .oldKeyPrefix(oldKey.getKeyPrefix())
            .newKeyPrefix(newKey.getKeyPrefix())
            .rotationType(request.getRotationType())
            .rotatedBy(rotatedBy)
            .rotationIp(rotationIp)
            .reason(request.getReason())
            .build();

        rotationHistoryRepository.save(history);

        log.info("[API-KEY] API key rotated successfully: oldKeyId={}, newKeyId={}, clientId={} (unchanged)",
            keyId, newKey.getPkExtensionApiKeyId(), oldKey.getClientId());

        // Build response
        ExtensionApiKeyResponse keyResponse = mapToResponse(newKey);
        return ExtensionApiKeyCreationResponse.builder()
            .key(keyResponse)
            .rawKey(rawKey)
            .warning("Store this API key securely. It will not be shown again.")
            .build();
    }

    /**
     * Extend API key expiry
     */
    @Transactional
    public ExtensionApiKeyResponse extendExpiry(String keyId, ExtendExtensionApiKeyExpiryRequest request,
                                                  String updatedBy) {
        log.info("[API-KEY] Extending API key expiry: keyId={}", keyId);

        if (!configService.isExpiryExtensionAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Expiry extension is not allowed by policy");
        }

        ExtensionApiKey apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        if ("REVOKED".equals(apiKey.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot extend expiry of revoked API key");
        }

        // Validate extension period
        LocalDateTime currentExpiry = apiKey.getExpiresAt();
        if (currentExpiry != null) {
            long daysDifference = java.time.Duration.between(
                currentExpiry, request.getNewExpiryDate()).toDays();

            if (daysDifference > configService.getMaxExpiryExtensionDays()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Extension exceeds maximum allowed days: " + configService.getMaxExpiryExtensionDays());
            }
        }

        apiKey.setExpiresAt(request.getNewExpiryDate());
        apiKey.setUpdatedBy(updatedBy);

        // Reactivate if expired
        if ("EXPIRED".equals(apiKey.getStatus())) {
            apiKey.setStatus("ACTIVE");
        }

        apiKey = apiKeyRepository.save(apiKey);

        log.info("[API-KEY] API key expiry extended successfully: keyId={}, newExpiry={}",
            keyId, request.getNewExpiryDate());

        return mapToResponse(apiKey);
    }

    /**
     * Get rotation history for a key
     */
    @Transactional(readOnly = true)
    public List<ExtensionApiKeyRotationHistory> getRotationHistory(String keyId) {
        return rotationHistoryRepository.findRotationChain(keyId);
    }

    /**
     * Get API key statistics
     */
    @Transactional(readOnly = true)
    public ApiKeyStatisticsResponse getStatistics(String tenantId) {
        long totalKeys = tenantId != null ?
            apiKeyRepository.countByTenantId(tenantId) :
            apiKeyRepository.count();

        long activeKeys = tenantId != null ?
            apiKeyRepository.countByTenantIdAndStatus(tenantId, "ACTIVE") :
            apiKeyRepository.countByTenantIdAndStatus(null, "ACTIVE");

        long inactiveKeys = tenantId != null ?
            apiKeyRepository.countByTenantIdAndStatus(tenantId, "INACTIVE") : 0;

        long revokedKeys = tenantId != null ?
            apiKeyRepository.countByTenantIdAndStatus(tenantId, "REVOKED") : 0;

        long expiredKeys = tenantId != null ?
            apiKeyRepository.countByTenantIdAndStatus(tenantId, "EXPIRED") : 0;

        LocalDateTime now = LocalDateTime.now();
        int warningDays = configService.getExpiryWarningThresholdDays();
        LocalDateTime threshold = now.plusDays(warningDays);

        long expiringSoonKeys = tenantId != null ?
            apiKeyRepository.countKeysExpiringSoon(tenantId, now, threshold) : 0;

        return ApiKeyStatisticsResponse.builder()
            .totalKeys(totalKeys)
            .activeKeys(activeKeys)
            .inactiveKeys(inactiveKeys)
            .revokedKeys(revokedKeys)
            .expiredKeys(expiredKeys)
            .expiringSoonKeys(expiringSoonKeys)
            .build();
    }

    /**
     * Validate API key (internal method)
     */
    @Transactional
    public ValidateExtensionApiKeyResponse validateApiKeyInternal(String rawKey) {
        String keyHash = ApiKeyUtil.hash(rawKey);

        Optional<ExtensionApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(keyHash);

        if (apiKeyOpt.isEmpty()) {
            return ValidateExtensionApiKeyResponse.builder()
                .valid(false)
                .errorCode("INVALID_KEY")
                .errorMessage("API key not found")
                .build();
        }

        ExtensionApiKey apiKey = apiKeyOpt.get();

        // Update last used timestamp
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);

        // Check status
        if (!"ACTIVE".equals(apiKey.getStatus())) {
            return ValidateExtensionApiKeyResponse.builder()
                .valid(false)
                .status(apiKey.getStatus())
                .errorCode("KEY_NOT_ACTIVE")
                .errorMessage("API key is not active: " + apiKey.getStatus())
                .build();
        }

        // Check expiry
        if (apiKey.isExpired()) {
            return ValidateExtensionApiKeyResponse.builder()
                .valid(false)
                .status("EXPIRED")
                .errorCode("KEY_EXPIRED")
                .errorMessage("API key has expired")
                .build();
        }

        return ValidateExtensionApiKeyResponse.builder()
            .valid(true)
            .tenantId(apiKey.getTenantId())
            .clientId(apiKey.getClientId())
            .status(apiKey.getStatus())
            .build();
    }

    /**
     * Map entity to response DTO
     */
    private ExtensionApiKeyResponse mapToResponse(ExtensionApiKey apiKey) {
        int warningDays = configService.getExpiryWarningThresholdDays();

        return ExtensionApiKeyResponse.builder()
            .pkExtensionApiKeyId(apiKey.getPkExtensionApiKeyId())
            .tenantId(apiKey.getTenantId())
            .keyPrefix(apiKey.getKeyPrefix())
            .clientId(apiKey.getClientId())
            .name(apiKey.getName())
            .description(apiKey.getDescription())
            .status(apiKey.getStatus())
            .expiresAt(apiKey.getExpiresAt())
            .lastUsedAt(apiKey.getLastUsedAt())
            .createdAt(apiKey.getCreatedAt())
            .updatedAt(apiKey.getUpdatedAt())
            .createdBy(apiKey.getCreatedBy())
            .updatedBy(apiKey.getUpdatedBy())
            .isExpired(apiKey.isExpired())
            .rawKey(apiKey.getKeyHash())
            .isExpiringSoon(apiKey.isExpiringSoon(warningDays))
            .daysRemaining(apiKey.getDaysRemaining())
            .build();
    }
}
