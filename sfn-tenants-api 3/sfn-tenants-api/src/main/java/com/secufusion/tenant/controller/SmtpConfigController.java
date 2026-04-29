package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.LoggedInUserDetailsBean;
import com.secufusion.tenant.entity.SmtpConfig;
import com.secufusion.tenant.service.SmtpConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tenants/smtp-config")
@Tag(name = "SMTP Configuration", description = "APIs for managing tenant SMTP settings")
public class SmtpConfigController {

    @Autowired
    private SmtpConfigService smtpConfigService;

    /* ==========================================================
                           CREATE - Tenant Specific
       ========================================================== */

    @Operation(summary = "Create SMTP configuration for current tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "SMTP config created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or config already exists"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    public ResponseEntity<SmtpConfig> createSmtpConfig(
            HttpServletRequest request,
            @RequestBody SmtpConfig smtpConfig
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER createSmtpConfig - tenantId={}", tenantId);
        SmtpConfig created = smtpConfigService.createSmtpConfig(tenantId, smtpConfig);
        log.info("EXIT createSmtpConfig - id={}", created.getPkSmtpConfigId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /* ==========================================================
                           GET - Current Tenant's Config
       ========================================================== */

    @Operation(summary = "Get SMTP configuration for current tenant (with fallback to default)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SMTP config fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ResponseEntity<SmtpConfig> getSmtpConfig(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getSmtpConfig - tenantId={}", tenantId);
        SmtpConfig config = smtpConfigService.getSmtpConfigForTenant(tenantId);
        log.info("EXIT getSmtpConfig - host={}", config.getHost());

        return ResponseEntity.ok(config);
    }

    /* ==========================================================
                           GET - Tenant's Own Config (without fallback)
       ========================================================== */

    @Operation(summary = "Get tenant's own SMTP configuration (without fallback)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SMTP config fetched successfully"),
            @ApiResponse(responseCode = "204", description = "No custom config exists"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/own")
    public ResponseEntity<SmtpConfig> getTenantOwnSmtpConfig(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER getTenantOwnSmtpConfig - tenantId={}", tenantId);
        SmtpConfig config = smtpConfigService.getTenantSmtpConfig(tenantId);

        if (config == null) {
            log.info("EXIT getTenantOwnSmtpConfig - no custom config");
            return ResponseEntity.noContent().build();
        }

        log.info("EXIT getTenantOwnSmtpConfig - host={}", config.getHost());
        return ResponseEntity.ok(config);
    }

    /* ==========================================================
                           UPDATE - Tenant Specific
       ========================================================== */

    @Operation(summary = "Update SMTP configuration for current tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SMTP config updated successfully"),
            @ApiResponse(responseCode = "404", description = "SMTP config not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping
    public ResponseEntity<SmtpConfig> updateSmtpConfig(
            HttpServletRequest request,
            @RequestBody SmtpConfig smtpConfig
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER updateSmtpConfig - tenantId={}", tenantId);
        SmtpConfig updated = smtpConfigService.updateSmtpConfig(tenantId, smtpConfig);
        log.info("EXIT updateSmtpConfig - id={}", updated.getPkSmtpConfigId());

        return ResponseEntity.ok(updated);
    }

    /* ==========================================================
                           DELETE - Tenant Specific
       ========================================================== */

    @Operation(summary = "Delete SMTP configuration for current tenant (reverts to default)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "SMTP config deleted successfully"),
            @ApiResponse(responseCode = "404", description = "SMTP config not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteSmtpConfig(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER deleteSmtpConfig - tenantId={}", tenantId);
        smtpConfigService.deleteSmtpConfig(tenantId);
        log.info("EXIT deleteSmtpConfig - tenantId={}", tenantId);

        return ResponseEntity.noContent().build();
    }

    /* ==========================================================
                           TEST - Connection Test
       ========================================================== */

    @Operation(summary = "Test SMTP connection with provided configuration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connection test result"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testSmtpConnection(
            HttpServletRequest request,
            @RequestBody SmtpConfig smtpConfig
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser != null ? loggedInUser.getTenantId() : null;

        log.info("ENTER testSmtpConnection - host={}, tenantId={}", smtpConfig.getHost(), tenantId);
        String error = smtpConfigService.testSmtpConnection(smtpConfig, tenantId);
        boolean success = (error == null);
        log.info("EXIT testSmtpConnection - success={}", success);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "SMTP connection successful" : error);
        return ResponseEntity.ok(response);
    }

    /* ==========================================================
                           TEST - Send Test Email
       ========================================================== */

    @Operation(summary = "Send a test email using provided SMTP configuration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Test email result"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/test-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(
            HttpServletRequest request,
            @RequestBody SmtpConfig smtpConfig,
            @RequestParam String toEmail
    ) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser != null ? loggedInUser.getTenantId() : null;

        log.info("ENTER sendTestEmail - host={}, toEmail={}, tenantId={}", smtpConfig.getHost(), toEmail, tenantId);
        String error = smtpConfigService.sendTestEmail(smtpConfig, toEmail, tenantId);
        boolean success = (error == null);
        log.info("EXIT sendTestEmail - success={}", success);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Test email sent successfully" : error);
        return ResponseEntity.ok(response);
    }

    /* ==========================================================
                           DEFAULT CONFIG - Get
       ========================================================== */

    @Operation(summary = "Get default/global SMTP configuration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Default SMTP config fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/default")
    public ResponseEntity<SmtpConfig> getDefaultSmtpConfig() {
        log.info("ENTER getDefaultSmtpConfig");
        SmtpConfig config = smtpConfigService.getDefaultSmtpConfig();
        log.info("EXIT getDefaultSmtpConfig - host={}", config.getHost());

        return ResponseEntity.ok(config);
    }

    /* ==========================================================
                           DEFAULT CONFIG - Create (Admin only)
       ========================================================== */

    @Operation(summary = "Create default/global SMTP configuration (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Default SMTP config created successfully"),
            @ApiResponse(responseCode = "400", description = "Default config already exists"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/default")
    public ResponseEntity<SmtpConfig> createDefaultSmtpConfig(
            @RequestBody SmtpConfig smtpConfig
    ) {
        log.info("ENTER createDefaultSmtpConfig - host={}", smtpConfig.getHost());
        SmtpConfig created = smtpConfigService.createDefaultSmtpConfig(smtpConfig);
        log.info("EXIT createDefaultSmtpConfig - id={}", created.getPkSmtpConfigId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /* ==========================================================
                           DEFAULT CONFIG - Update (Admin only)
       ========================================================== */

    @Operation(summary = "Update default/global SMTP configuration (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Default SMTP config updated successfully"),
            @ApiResponse(responseCode = "404", description = "Default config not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/default")
    public ResponseEntity<SmtpConfig> updateDefaultSmtpConfig(
            @RequestBody SmtpConfig smtpConfig
    ) {
        log.info("ENTER updateDefaultSmtpConfig - host={}", smtpConfig.getHost());
        SmtpConfig updated = smtpConfigService.updateDefaultSmtpConfig(smtpConfig);
        log.info("EXIT updateDefaultSmtpConfig - id={}", updated.getPkSmtpConfigId());

        return ResponseEntity.ok(updated);
    }

    /* ==========================================================
                           SYNC TO KEYCLOAK - Tenant Specific
       ========================================================== */

    @Operation(summary = "Sync SMTP configuration to tenant's Keycloak realm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SMTP synced to Keycloak successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to sync to Keycloak"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/sync-keycloak")
    public ResponseEntity<Map<String, Object>> syncSmtpToKeycloak(HttpServletRequest request) {
        LoggedInUserDetailsBean loggedInUser =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUser.getTenantId();

        log.info("ENTER syncSmtpToKeycloak - tenantId={}", tenantId);
        boolean success = smtpConfigService.syncSmtpToKeycloak(tenantId);
        log.info("EXIT syncSmtpToKeycloak - success={}", success);

        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "SMTP settings synced to Keycloak successfully" : "Failed to sync SMTP settings to Keycloak"
        ));
    }

    /* ==========================================================
                           SYNC DEFAULT TO ALL REALMS (Admin only)
       ========================================================== */

    @Operation(summary = "Sync default SMTP configuration to all tenant Keycloak realms (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SMTP synced to all realms"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/default/sync-all")
    public ResponseEntity<Map<String, Object>> syncDefaultToAllRealms() {
        log.info("ENTER syncDefaultToAllRealms");
        int count = smtpConfigService.syncDefaultSmtpToAllRealms();
        log.info("EXIT syncDefaultToAllRealms - synced {} realms", count);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "realmsUpdated", count,
                "message", "Default SMTP settings synced to " + count + " realms"
        ));
    }
}
