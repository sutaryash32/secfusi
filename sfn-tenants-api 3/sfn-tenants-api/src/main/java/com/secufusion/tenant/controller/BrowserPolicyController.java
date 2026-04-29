package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.BrowserPolicyWithAssignmentsDto;
import com.secufusion.tenant.dto.LoggedInUserDetailsBean;
import com.secufusion.tenant.dto.PolicyVersionResponse;
import com.secufusion.tenant.dto.UserEffectivePolicyResponse;
import com.secufusion.tenant.entity.BrowserPolicy;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.service.BrowserPolicyService;
import com.secufusion.tenant.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * REST controller for Browser Policy management.
 *
 * <p>Provides CRUD endpoints for browser policies.
 * Each endpoint includes request validation, logging
 * and appropriate HTTP response handling.
 */
@Slf4j
@RestController
@RequestMapping("/api/tenants/policy")
public class BrowserPolicyController {

    @Autowired
    private BrowserPolicyService browserPolicyService;

    @Autowired
    private JwtUtl jwtUtl;

    /* ==========================================================
                               CREATE
       ========================================================== */

    @PostMapping
    public ResponseEntity<BrowserPolicy> createPolicy(
            HttpServletRequest request,
            @RequestBody BrowserPolicy policy
    ) {
        log.debug("API request: Create BrowserPolicy");

        LoggedInUserDetailsBean loggedInUserDetailsBean =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");

        String tenantId = loggedInUserDetailsBean.getTenantId();
        policy.setFkTenantId(tenantId);

        BrowserPolicy created = browserPolicyService.createPolicy(policy);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /* ==========================================================
                              GET BY ID
       ========================================================== */

    @GetMapping("/{id}")
    public ResponseEntity<BrowserPolicy> getPolicy(@PathVariable String id) {
        log.debug("API request: Get BrowserPolicy id={}", id);
        return ResponseEntity.ok(browserPolicyService.getPolicyById(id));
    }

    /* ==========================================================
                              GET ALL
       ========================================================== */

    @GetMapping
    public ResponseEntity<List<BrowserPolicy>> getAllPolicies(HttpServletRequest request) {
        log.debug("API request: Get effective BrowserPolicy for current user");

        try {
            // 1. Resolve the single effective policy for this user
            BrowserPolicy effectivePolicy = browserPolicyService.resolvePolicyForCurrentUser(request);

            // 2. Wrap it in a List to satisfy the API signature
            // If the service returns null (fallback failed), we return an empty list to be safe
            if (effectivePolicy == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            return ResponseEntity.ok(List.of(effectivePolicy));

        } catch (SecurityException e) {
            log.error("Security/Context Error: {}", e.getMessage());
            // Return 401 or 403 if tenant/user context is missing
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Unexpected error fetching policy", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /* ==========================================================
                              UPDATE
       ========================================================== */

    @PutMapping("/{id}")
    public ResponseEntity<BrowserPolicy> updatePolicy(
            @PathVariable String id,
            @RequestBody BrowserPolicy policy,
            HttpServletRequest request
    ) {
        log.debug("API request: Update BrowserPolicy id={}", id);

        BrowserPolicy updated =
                browserPolicyService.updatePolicy(id, policy, request);

        return ResponseEntity.ok(updated);
    }

    /* ==========================================================
                              DELETE
       ========================================================== */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String id) {
        log.debug("API request: Delete BrowserPolicy id={}", id);

        browserPolicyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    /* ==========================================================
                     AUDIT / REVISION HISTORY
       ========================================================== */

    /**
     * Get all audit revisions for a BrowserPolicy
     */
    @GetMapping("/{id}/revisions")
    public ResponseEntity<List<Object[]>> getPolicyRevisions(
            @PathVariable String id
    ) {
        log.debug("API request: Get BrowserPolicy revisions id={}", id);
        return ResponseEntity.ok(browserPolicyService.getPolicyRevisions(id));
    }

    /**
     * Get BrowserPolicy state at a specific revision
     */
    @GetMapping("/{id}/revisions/{rev}")
    public ResponseEntity<BrowserPolicy> getPolicyAtRevision(
            @PathVariable String id,
            @PathVariable Number rev
    ) {
        log.debug(
                "API request: Get BrowserPolicy id={} at revision={}",
                id,
                rev
        );

        return ResponseEntity.ok(
                browserPolicyService.getPolicyAtRevision(id, rev)
        );
    }

    @GetMapping("/all")
    public ResponseEntity<List<BrowserPolicy>> getAllPoliciesForTenant(HttpServletRequest request) {
        log.debug("API request: Get all BrowserPolicies for tenant");

        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        if (tenantFromRequest == null || tenantFromRequest.getTenantID() == null) {
            log.error("Tenant context missing in token for GET /policy/all");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(
                browserPolicyService.getAllPolicies(tenantFromRequest.getTenantID())
        );
    }

    /* ==========================================================
                     GET DEFAULT POLICY (fkTenantId = null)
       ========================================================== */

    /**
     * Get the global default BrowserPolicy (fkTenantId = null)
     */
    @GetMapping("/default")
    public ResponseEntity<BrowserPolicy> getGlobalDefaultPolicy() {
        log.info("ENTER getGlobalDefaultPolicy");

        BrowserPolicy defaultPolicy = browserPolicyService.createdDefaultPolicyIfNotExists();

        log.info("EXIT getGlobalDefaultPolicy - policyId={}", defaultPolicy.getPkBrowserPolicyId());
        return ResponseEntity.ok(defaultPolicy);
    }

    /* ==========================================================
                 POLICY WITH ASSIGNMENT STATUS
       ========================================================== */

    /**
     * Get a single policy by ID with assignment status.
     * Returns policy details along with list of Azure role/group assignments.
     */
    @GetMapping("/{id}/with-assignments")
    public ResponseEntity<BrowserPolicyWithAssignmentsDto> getPolicyWithAssignments(
            @PathVariable String id
    ) {
        log.debug("API request: Get BrowserPolicy with assignments, id={}", id);
        return ResponseEntity.ok(browserPolicyService.getPolicyByIdWithAssignments(id));
    }

    /**
     * Get all policies for tenant with assignment status.
     * Returns list of policies with their Azure role/group assignments.
     */
    @GetMapping("/all/with-assignments")
    public ResponseEntity<List<BrowserPolicyWithAssignmentsDto>> getAllPoliciesWithAssignments(
            HttpServletRequest request
    ) {
        log.debug("API request: Get all BrowserPolicies with assignments for tenant");

        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        if (tenantFromRequest == null || tenantFromRequest.getTenantID() == null) {
            log.error("Tenant context missing in token for GET /policy/all/with-assignments");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(
                browserPolicyService.getAllPoliciesWithAssignments(tenantFromRequest.getTenantID())
        );
    }

    /**
     * Get all effective policies (BrowserPolicy + NetworkPolicy + ExtensionPolicy)
     * assigned to the current user's group/role.
     * New endpoint — does not overlap with any existing endpoint.
     */
    @GetMapping("/effective/all")
    public ResponseEntity<UserEffectivePolicyResponse> getAllEffectivePolicies(HttpServletRequest request) {
        log.debug("API request: Get all effective policies for current user");

        try {
            UserEffectivePolicyResponse response = browserPolicyService.resolveAllPoliciesForCurrentUser(request);
            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.error("Security/Context Error: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Unexpected error fetching all effective policies", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get effective policy for current user with assignment status.
     * Resolves the policy based on user's JWT claims (roles/groups).
     */
    @GetMapping("/effective/with-assignments")
    public ResponseEntity<BrowserPolicyWithAssignmentsDto> getEffectivePolicyWithAssignments(
            HttpServletRequest request
    ) {
        log.debug("API request: Get effective BrowserPolicy with assignments for current user");

        try {
            BrowserPolicyWithAssignmentsDto effectivePolicy =
                    browserPolicyService.resolveEffectivePolicyWithAssignments(request);

            if (effectivePolicy == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(effectivePolicy);

        } catch (SecurityException e) {
            log.error("Security/Context Error: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Unexpected error fetching policy with assignments", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /* ==========================================================
                 POLICY VERSION CHECK (Extension polling)
       ========================================================== */

    /**
     * Lightweight version check for all three policy types.
     * The extension polls this instead of fetching full policies.
     * Only when policyId or version changes should the extension call /effective/all.
     */
    @GetMapping("/effective/versions")
    public ResponseEntity<PolicyVersionResponse> getPolicyVersions(HttpServletRequest request) {
        log.debug("API request: Get policy versions for current user");

        try {
            PolicyVersionResponse versions = browserPolicyService.getPolicyVersionsForCurrentUser(request);
            return ResponseEntity.ok(versions);

        } catch (SecurityException e) {
            log.error("Security/Context Error: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Unexpected error fetching policy versions", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}