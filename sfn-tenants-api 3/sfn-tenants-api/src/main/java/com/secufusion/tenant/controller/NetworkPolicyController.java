package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.NetworkPolicyRequestDTO;
import com.secufusion.tenant.dto.NetworkPolicyResponseDTO;
import com.secufusion.tenant.service.NetworkPolicyService;
import com.secufusion.tenant.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing CRUD operations for Network Policy entities scoped to a Tenant.
 *
 * <p>Each endpoint extracts the tenant identifier from the incoming request's JWT using
 * {@link JwtUtl} and delegates business operations to {@link NetworkPolicyService}.</p>
 *
 * <p>Logs are emitted at method entry and exit to aid debugging and tracing of tenant-scoped operations.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/tenants/network-policy")
@Tag(
        name = "Network Policy",
        description = "APIs for managing Network Policies for a Tenant"
)
public class NetworkPolicyController {

    @Autowired
    private NetworkPolicyService networkPolicyService;

    @Autowired
    private JwtUtl jwtUtl;

    /* ===================== CREATE ===================== */

    /**
     * Create a new Network Policy for the tenant identified in the request JWT.
     *
     * @param request the HTTP request containing the authorization JWT
     * @param dto     the network policy payload
     * @return ResponseEntity containing the created NetworkPolicyResponseDTO with HTTP 201 status
     */
    @Operation(summary = "Create Network Policy")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Network Policy created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    public ResponseEntity<NetworkPolicyResponseDTO> create(
            HttpServletRequest request,
            @Valid @RequestBody NetworkPolicyRequestDTO dto) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("ENTER create - tenantId={}, dto={}", tenantId, dto);

        NetworkPolicyResponseDTO response =
                networkPolicyService.create(dto, tenantId);

        log.info("EXIT create - tenantId={}, createdPolicyId={}", tenantId, response != null ? response.getNetworkPolicyId() : "null");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /* ===================== GET BY ID ===================== */

    /**
     * Retrieve a Network Policy by id scoped to the tenant from the request JWT.
     *
     * @param id      the network policy identifier
     * @param request the HTTP request containing the authorization JWT
     * @return ResponseEntity with the requested NetworkPolicyResponseDTO and HTTP 200 status
     */
    @Operation(summary = "Get Network Policy by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Network Policy fetched successfully"),
            @ApiResponse(responseCode = "404", description = "Network Policy not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NetworkPolicyResponseDTO> getById(
            @PathVariable String id,
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("ENTER getById - id={}, tenantId={}", id, tenantId);

        NetworkPolicyResponseDTO response =
                networkPolicyService.getById(id, tenantId);

        log.debug("EXIT getById - id={}, tenantId={}, found={}", id, tenantId, response != null);
        return ResponseEntity.ok(response);
    }

    /* ===================== GET ALL ===================== */

    /**
     * Retrieve all Network Policies for the tenant contained in the request JWT.
     *
     * @param request the HTTP request containing the authorization JWT
     * @return ResponseEntity with a list of NetworkPolicyResponseDTO and HTTP 200 status
     */
    @Operation(summary = "Get all Network Policies for a Tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Network Policies fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ResponseEntity<List<NetworkPolicyResponseDTO>> getAll(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.debug("ENTER getAll - tenantId={}", tenantId);

        List<NetworkPolicyResponseDTO> policies = networkPolicyService.getAll(tenantId);

        log.debug("EXIT getAll - tenantId={}, count={}", tenantId, policies != null ? policies.size() : 0);
        return ResponseEntity.ok(policies);
    }

    /* ===================== UPDATE ===================== */

    /**
     * Update an existing Network Policy for the tenant extracted from the request JWT.
     *
     * @param id      the network policy identifier to update
     * @param dto     the new network policy data
     * @param request the HTTP request containing the authorization JWT
     * @return ResponseEntity containing the updated NetworkPolicyResponseDTO and HTTP 200 status
     */
    @Operation(summary = "Update Network Policy")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Network Policy updated successfully"),
            @ApiResponse(responseCode = "404", description = "Network Policy not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/{id}")
    public ResponseEntity<NetworkPolicyResponseDTO> update(
            @PathVariable String id,
            @Valid @RequestBody NetworkPolicyRequestDTO dto,
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("ENTER update - id={}, tenantId={}, dto={}", id, tenantId, dto);

        NetworkPolicyResponseDTO response =
                networkPolicyService.update(id, dto, tenantId);

        log.info("EXIT update - id={}, tenantId={}, updatedPolicyId={}", id, tenantId, response != null ? response.getNetworkPolicyId() : "null");
        return ResponseEntity.ok(response);
    }

    /* ===================== DELETE ===================== */

    /**
     * Delete a Network Policy by id for the tenant from the request JWT.
     *
     * @param id      the network policy identifier to delete
     * @param request the HTTP request containing the authorization JWT
     * @return ResponseEntity with HTTP 204 status on successful deletion
     */
    @Operation(summary = "Delete Network Policy")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Network Policy deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Network Policy not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.warn("ENTER delete - id={}, tenantId={}", id, tenantId);

        networkPolicyService.delete(id, tenantId);

        log.warn("EXIT delete - id={}, tenantId={} (deleted)", id, tenantId);
        return ResponseEntity.noContent().build();
    }
}