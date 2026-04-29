package com.secufusion.events.controller;

import com.secufusion.events.dto.apikey.*;
import com.secufusion.events.service.ExtensionTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ExtensionApiKeyPublicController
 *
 * Public REST controller for Extension API Key validation and token generation.
 * These endpoints are called by browser extensions and do not require authentication.
 */
@RestController
@RequestMapping("/api/events/public/extension")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Extension API Key Public", description = "Public endpoints for extension authentication")
public class ExtensionApiKeyPublicController {

    private final ExtensionTokenService tokenService;

    @PostMapping("/validate")
    @Operation(
        summary = "Validate API key",
        description = "Validate an API key without generating a token (read-only check)"
    )
    public ResponseEntity<ValidateExtensionApiKeyResponse> validateApiKey(
        @Valid @RequestBody ValidateExtensionApiKeyRequest request
    ) {
        log.info("[EXTENSION-PUBLIC] Validating API key");
        ValidateExtensionApiKeyResponse response = tokenService.validateApiKey(request.getApiKey());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-token")
    @Operation(
        summary = "Generate JWT token",
        description = "Generate a JWT token using API key and device user details"
    )
    public ResponseEntity<GenerateTokenResponse> generateToken(
        @Valid @RequestBody GenerateTokenRequest request
    ) {
        log.info("[EXTENSION-PUBLIC] Generating token for email={}",
            request.getDeviceUserDetails().getEmail());
        GenerateTokenResponse response = tokenService.generateToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-token-with-steps")
    @Operation(
        summary = "Generate token with validation steps",
        description = "Generate JWT token with detailed step-by-step validation progress"
    )
    public ResponseEntity<TokenValidationStepResponse> generateTokenWithSteps(
        @Valid @RequestBody GenerateTokenRequest request
    ) {
        log.info("[EXTENSION-PUBLIC] Generating token with steps for email={}",
            request.getDeviceUserDetails().getEmail());
        TokenValidationStepResponse response = tokenService.generateTokenWithSteps(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-expiry")
    @Operation(
        summary = "Check API key expiry",
        description = "Check if an API key is expired or expiring soon"
    )
    public ResponseEntity<ApiKeyExpiryCheckResponse> checkExpiry(
        @Valid @RequestBody ValidateExtensionApiKeyRequest request
    ) {
        log.info("[EXTENSION-PUBLIC] Checking API key expiry");
        ApiKeyExpiryCheckResponse response = tokenService.checkExpiry(request.getApiKey());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/usage-stats")
    @Operation(
        summary = "Get API key usage stats",
        description = "Get usage statistics for an API key (device users, activity)"
    )
    public ResponseEntity<ApiKeyUsageStatsResponse> getUsageStats(
        @Valid @RequestBody ValidateExtensionApiKeyRequest request
    ) {
        log.info("[EXTENSION-PUBLIC] Getting usage stats");
        ApiKeyUsageStatsResponse response = tokenService.getUsageStats(request.getApiKey());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    @Operation(
        summary = "Refresh JWT token",
        description = "Generate a new JWT token (same as generate-token, kept for compatibility)"
    )
    public ResponseEntity<GenerateTokenResponse> refreshToken(
        @Valid @RequestBody GenerateTokenRequest request
    ) {
        log.info("[EXTENSION-PUBLIC] Refreshing token for email={}",
            request.getDeviceUserDetails().getEmail());
        GenerateTokenResponse response = tokenService.generateToken(request);
        return ResponseEntity.ok(response);
    }
}
