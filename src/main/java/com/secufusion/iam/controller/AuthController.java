package com.secufusion.iam.controller;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.dto.DeviceInfoRequest;
import com.secufusion.iam.dto.LoginResponseDto;
import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.dto.SsoLoginResponseDto;
import com.secufusion.iam.service.AuthConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping
@Tag(name = "Auth", description = "Authentication configuration endpoints")
public class AuthController {

    @Autowired
    private AuthConfigService authConfigService;

    @Operation(summary = "Get tenant config (validated)",
            description = "Returns tenant authentication configuration after validating the Referer header and request host.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant configuration found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthDetailsDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - invalid referer or host"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Parameter(name = "Referer", in = ParameterIn.HEADER, description = "Referer header containing origin URL", required = true)
    @GetMapping("/tenant-config/v1")
    public ResponseEntity<ResponseDto<AuthDetailsDto>> getTenantConfig(
            HttpServletRequest request,
            @Parameter(description = "Expected host/domain for validation", required = true)
            @RequestParam String host
    ) {

        // Extract referer
        String refererHeader = request.getHeader("Referer");
        if (refererHeader == null || refererHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // Domain from referer
            URI refererUri = new URI(refererHeader);
            String refererDomain = refererUri.getHost();

            // Domain from request URL
            URI requestUri = new URI(request.getRequestURL().toString());
            String requestDomain = requestUri.getHost();

            // Normalize host param
            String expectedDomain = host.toLowerCase().trim();

            log.info("Validation check => requestDomain={}, refererDomain={}, hostParam={}",
                    requestDomain, refererDomain, expectedDomain);

            // STRICT MATCHING RULES
            if (!expectedDomain.equalsIgnoreCase(refererDomain) ||
                    !expectedDomain.equalsIgnoreCase(requestDomain) ||
                    !refererDomain.equalsIgnoreCase(requestDomain)) {

                log.warn("Domain validation failed");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Passed all checks → return config
            return ResponseEntity.ok(
                    new ResponseDto<>(
                            authConfigService.getTenantConfig(expectedDomain),
                            String.valueOf(HttpStatus.OK)
                    )
            );

        } catch (Exception e) {
            log.error("Error validating referer", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Operation(summary = "Get tenant config (no validation)",
            description = "Returns tenant authentication configuration without referer/host validation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant configuration found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthDetailsDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/tenant-config")
    public ResponseEntity<ResponseDto<AuthDetailsDto>> getTenantConfig(@Parameter(description = "Host/domain", required = true) @RequestParam String host) {
//        String flagKey = "api.users.get.enabled";
//        if (!featureFlagService.isApiEnabled(flagKey)) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
        return ResponseEntity.ok(
                new ResponseDto<>(
                        authConfigService.getTenantConfig(host),
                        String.valueOf(HttpStatus.OK)
                )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseDto<LoginResponseDto>> login(
            HttpServletRequest request,
            @RequestParam String token,
            @RequestBody(required = false) DeviceInfoRequest deviceInfo) {

        // Mask token info: don't log the token itself, only its length and presence
        String remoteAddr = request.getRemoteAddr();
        int tokenLength = token == null ? 0 : token.length();
        log.info("Login attempt from remoteAddr={} with tokenPresent={} tokenLength={} deviceInfoPresent={}",
                remoteAddr, token != null && !token.isBlank(), tokenLength > 0 ? tokenLength : 0,
                deviceInfo != null && deviceInfo.getDeviceFingerprint() != null);

        // Delegate authentication to service with device info
        LoginResponseDto response = authConfigService.login(request, token, deviceInfo);

        log.debug("Login processed for remoteAddr={}, resultStatus={}",
                remoteAddr, response != null ? "non-null" : "null");

        return ResponseEntity.ok(
                new ResponseDto<>(
                        response,
                        String.valueOf(HttpStatus.OK)
                )
        );
    }

    @Operation(summary = "Extension Login",
            description = "Authenticates browser extension with JWT token and optional device info for device tracking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/login/extension")
    public ResponseEntity<ResponseDto<LoginResponseDto>> extensionLogin(
            HttpServletRequest request,
            @RequestParam String token,
            @RequestBody(required = false) DeviceInfoRequest deviceInfo) {

        // Mask token info: don't log the token itself, only its length and presence
        String remoteAddr = request.getRemoteAddr();
        int tokenLength = token == null ? 0 : token.length();
        log.info("Extension login attempt from remoteAddr={} with tokenPresent={} tokenLength={} deviceInfoPresent={}",
                remoteAddr, token != null && !token.isBlank(), tokenLength > 0 ? tokenLength : 0,
                deviceInfo != null && deviceInfo.getDeviceFingerprint() != null);

        // Delegate authentication to service with device info
        LoginResponseDto response = authConfigService.login(request, token, deviceInfo);

        log.debug("Extension login processed for remoteAddr={}, resultStatus={}",
                remoteAddr, response != null ? "non-null" : "null");

        return ResponseEntity.ok(
                new ResponseDto<>(
                        response,
                        String.valueOf(HttpStatus.OK)
                )
        );
    }

    @Operation(summary = "SSO Login",
            description = "Validates Azure tenant ID from JWT token against registered SSO configurations and returns authorization status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSO authentication successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SsoLoginResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired token"),
            @ApiResponse(responseCode = "404", description = "SSO tenant not registered or SSO not enabled"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/login/sso")
    public ResponseEntity<ResponseDto<SsoLoginResponseDto>> ssoLogin(
            HttpServletRequest request,
            @RequestParam String token) {

        // Mask token info: don't log the token itself, only its length and presence
        String remoteAddr = request.getRemoteAddr();
        int tokenLength = token == null ? 0 : token.length();
        log.info("SSO Login attempt from remoteAddr={} with tokenPresent={} tokenLength={}",
                remoteAddr, token != null && !token.isBlank(), tokenLength > 0 ? tokenLength : 0);

        // Delegate SSO authentication to service
        SsoLoginResponseDto response = authConfigService.ssoLogin(request, token);

        log.debug("SSO Login processed for remoteAddr={}, authorized={}",
                remoteAddr, response != null ? response.isAuthorized() : "null");

        return ResponseEntity.ok(
                new ResponseDto<>(
                        response,
                        String.valueOf(HttpStatus.OK)
                )
        );
    }

}