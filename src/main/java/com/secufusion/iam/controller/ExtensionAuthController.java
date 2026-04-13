package com.secufusion.iam.controller;

import com.secufusion.iam.entity.EventsGroup;
import com.secufusion.iam.entity.PolicyAssignment;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.AccessDeniedException;
import com.secufusion.iam.exception.BadRequestException;
import com.secufusion.iam.repository.EventsGroupRepository;
import com.secufusion.iam.repository.PolicyAssignmentRepository;
import com.secufusion.iam.util.JwtUtl;
import org.springframework.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ExtensionAuthController
 *
 * Handles browser extension authentication for AZURE SSO tenants.
 *
 * Flow:
 * 1. Extension sends request with the user's Keycloak JWT (Bearer token)
 * 2. Extract tenant from JWT (azp claim)
 * 3. Extract Azure AD group IDs from JWT (groups claim)
 * 4. Check if any of those groups have extensionAuthorized=true in events_groups table
 * 5. If authorized → find matching group → load ExtensionPolicy via PolicyAssignment
 * 6. Return ExtensionPolicy to the browser extension
 *
 * Gate logic:
 * - authorized       = controls main app login (also gates Keycloak essential claim)
 * - extensionAuthorized = controls browser extension login (this controller)
 * These are independent — a group can be authorized for main login but not extension, and vice versa (though
 * extensionAuthorized requires authorized=true first as a prerequisite set at authorization time).
 */
@RestController
@RequestMapping("/extension")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Extension Auth", description = "Browser extension authentication for Azure SSO tenants")
public class ExtensionAuthController {

    private final JwtUtl jwtUtil;
    private final EventsGroupRepository eventsGroupRepository;
    private final PolicyAssignmentRepository policyAssignmentRepository;

    /**
     * Authenticate browser extension user.
     *
     * The extension sends the user's existing Keycloak app JWT as Bearer token.
     * This endpoint validates extension access and returns the applicable ExtensionPolicy.
     *
     * POST /extension/auth
     * Authorization: Bearer <keycloak_jwt>
     */
    @PostMapping("/auth")
    @Transactional(readOnly = true)
    @Operation(
        summary = "Authenticate browser extension (AZURE tenants)",
        description = "Validates that the authenticated user's Azure AD group has extension access enabled.\n\n" +
                     "**Request:** Send the user's Keycloak JWT as `Authorization: Bearer <token>`\n\n" +
                     "**Flow:**\n" +
                     "1. Extracts tenant from JWT `azp` claim\n" +
                     "2. Extracts Azure AD group IDs from JWT `groups` claim\n" +
                     "3. Checks `extensionAuthorized=true` on matching EventsGroup\n" +
                     "4. Returns ExtensionPolicy settings for the browser extension\n\n" +
                     "**403 Returned When:**\n" +
                     "- Tenant is not AZURE type\n" +
                     "- User has no Azure group IDs in token\n" +
                     "- None of the user's groups have `extensionAuthorized=true`"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Extension access granted — returns ExtensionPolicy"),
        @ApiResponse(responseCode = "400", description = "Bad request — missing token or not an AZURE tenant"),
        @ApiResponse(responseCode = "403", description = "Extension access denied — group not authorized for extension")
    })
    public ResponseEntity<Map<String, Object>> authenticateExtension(HttpServletRequest request) {

        // 1. Resolve tenant from JWT
        Tenant tenant = jwtUtil.getTenantFromRequest(request);
        if (tenant == null) {
            throw new BadRequestException("Unable to resolve tenant from token");
        }

        String tenantId = tenant.getTenantID();
        String ssoType = tenant.getAuthProviderConfig() != null
                ? tenant.getAuthProviderConfig().getSsoType()
                : null;

        if (!"AZURE".equalsIgnoreCase(ssoType)) {
            log.warn("[EXT-AUTH] Rejected: tenant {} is not AZURE type (ssoType={})", tenantId, ssoType);
            throw new BadRequestException("Extension authentication is only supported for AZURE SSO tenants");
        }

        // 2. Extract Azure group IDs from JWT groups claim
        String authHeader = request.getHeader("Authorization");
        String rawToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;
        List<String> azureGroupIds = jwtUtil.getGroupsFromToken(rawToken);

        if (azureGroupIds == null || azureGroupIds.isEmpty()) {
            log.warn("[EXT-AUTH] Rejected: no Azure group IDs in token for tenant {}", tenantId);
            throw new AccessDeniedException("No Azure group membership found in token. Extension access denied.");
        }

        log.info("[EXT-AUTH] Checking extension access for tenant={} groupCount={}", tenantId, azureGroupIds.size());

        // 3. Check if any of the user's groups have extensionAuthorized=true
        boolean hasExtensionAccess = eventsGroupRepository
                .existsExtensionAuthorizedAzureGroup(tenantId, azureGroupIds);

        if (!hasExtensionAccess) {
            log.warn("[EXT-AUTH] Denied: no extension-authorized group found for tenant={} groups={}",
                    tenantId, azureGroupIds);
            throw new AccessDeniedException(
                    "Your Azure AD group does not have browser extension access. " +
                    "Contact your administrator to enable extension access for your group.");
        }

        // 4. Find the matching extension-authorized groups
        List<EventsGroup> matchedGroups = eventsGroupRepository
                .findExtensionAuthorizedGroups(tenantId, azureGroupIds);

        if (matchedGroups.isEmpty()) {
            throw new AccessDeniedException("Extension access denied.");
        }

        // 5. Pick the first matched group (lowest createdAt = earliest authorized = highest priority)
        EventsGroup group = matchedGroups.get(0);
        String groupId = group.getPkEventsGroupId();

        log.info("[EXT-AUTH] Matched group='{}' (id={}) for tenant={}", group.getName(), groupId, tenantId);

        // 6. Load PolicyAssignment rows for this group
        List<PolicyAssignment> assignments = policyAssignmentRepository
                .findByEventsGroupIdAndTenantId(groupId, tenantId);

        // 7. Extract ExtensionPolicy from assignments (one row per policy type)
        PolicyAssignment extensionAssignment = assignments.stream()
                .filter(a -> a.getExtensionPolicy() != null)
                .findFirst()
                .orElse(null);

        // 8. Build response
        Map<String, Object> response = new HashMap<>();
        response.put("granted", true);
        response.put("tenantId", tenantId);
        response.put("groupId", groupId);
        response.put("groupName", group.getName());

        if (extensionAssignment != null && extensionAssignment.getExtensionPolicy() != null) {
            Map<String, Object> policyMap = new HashMap<>();
            policyMap.put("extensionPolicyId", extensionAssignment.getExtensionPolicy().getPkExtensionPolicyId());
            policyMap.put("name", extensionAssignment.getExtensionPolicy().getName());
            policyMap.put("policyKey", extensionAssignment.getExtensionPolicy().getPolicyKey());
            policyMap.put("version", extensionAssignment.getExtensionPolicy().getVersion());
            policyMap.put("landingPageUrl", extensionAssignment.getExtensionPolicy().getLandingPageUrl());
            response.put("extensionPolicy", policyMap);
        } else {
            log.warn("[EXT-AUTH] No ExtensionPolicy found for group='{}' tenant={}", group.getName(), tenantId);
            response.put("extensionPolicy", null);
        }

        log.info("[EXT-AUTH] Access granted for tenant={} group='{}'", tenantId, group.getName());
        return ResponseEntity.ok(response);
    }
}
