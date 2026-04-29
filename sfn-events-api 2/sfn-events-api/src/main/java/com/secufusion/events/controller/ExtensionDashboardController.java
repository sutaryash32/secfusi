package com.secufusion.events.controller;

import com.secufusion.events.dto.ExtensionDashboardDTO;
import com.secufusion.events.dto.ResponseDto;
import com.secufusion.events.service.ExtensionDashboardService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/extensions/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Extension Dashboard", description = "Extension management dashboard APIs for analytics, inventory, and bulk actions")
public class ExtensionDashboardController {

    private final ExtensionDashboardService dashboardService;
    private final JwtUtl jwtUtl;

    // ==================== 1. OVERVIEW ====================

    @GetMapping("/overview")
    @Operation(summary = "Dashboard overview",
            description = "Get KPI cards, distribution charts, last 30 days summary, top high-risk extensions, and recent events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Overview retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<ExtensionDashboardDTO.Overview>> getOverview(HttpServletRequest request) {
        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        ExtensionDashboardDTO.Overview overview = dashboardService.getOverview(tenantId);
        return ResponseEntity.ok(new ResponseDto<>(overview, String.valueOf(HttpStatus.OK.value())));
    }

    // ==================== 2. INVENTORY ====================

    @GetMapping("/inventory")
    @Operation(summary = "Extension inventory",
            description = "Deduplicated extension catalog with aggregate counts, filterable by risk level, policy action, and search term")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<ExtensionDashboardDTO.InventoryItem>>> getInventory(
            HttpServletRequest request,
            @Parameter(description = "Filter by risk level (HIGH, MEDIUM, LOW, NONE)") @RequestParam(required = false) String riskLevel,
            @Parameter(description = "Filter by policy action (ALLOW, WARN, BLOCK)") @RequestParam(required = false) String policyAction,
            @Parameter(description = "Search by extension name or ID") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        Page<ExtensionDashboardDTO.InventoryItem> inventory = dashboardService.getInventory(
                tenantId, riskLevel, policyAction, search, page, size);
        return ResponseEntity.ok(new ResponseDto<>(inventory, String.valueOf(HttpStatus.OK.value())));
    }

    // ==================== 3. EXTENSION DETAIL ====================

    @GetMapping("/inventory/{extensionId}")
    @Operation(summary = "Extension detail",
            description = "Complete detail for a single extension including all installations, version history, and event history")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Extension detail retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Extension not found")
    })
    public ResponseEntity<ResponseDto<ExtensionDashboardDTO.ExtensionDetail>> getExtensionDetail(
            HttpServletRequest request,
            @Parameter(description = "Chrome/Edge extension ID") @PathVariable String extensionId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        ExtensionDashboardDTO.ExtensionDetail detail = dashboardService.getExtensionDetail(tenantId, extensionId);
        return ResponseEntity.ok(new ResponseDto<>(detail, String.valueOf(HttpStatus.OK.value())));
    }

    // ==================== 4. TRENDS ====================

    @GetMapping("/trends")
    @Operation(summary = "Trend analytics",
            description = "Time-series data for charts: daily install/uninstall/block/warn activity, top new and removed extensions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trends retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<ExtensionDashboardDTO.Trends>> getTrends(
            HttpServletRequest request,
            @Parameter(description = "Number of days to look back (default 30)") @RequestParam(defaultValue = "30") int days) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        ExtensionDashboardDTO.Trends trends = dashboardService.getTrends(tenantId, days);
        return ResponseEntity.ok(new ResponseDto<>(trends, String.valueOf(HttpStatus.OK.value())));
    }

    // ==================== 5. USER RISK PROFILES ====================

    @GetMapping("/user-risk")
    @Operation(summary = "User risk profiles",
            description = "Per-user extension risk summary ranked by risk score, showing extension counts and risk levels")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User risk profiles retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<ExtensionDashboardDTO.UserRiskProfile>>> getUserRiskProfiles(
            HttpServletRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        Page<ExtensionDashboardDTO.UserRiskProfile> profiles = dashboardService.getUserRiskProfiles(tenantId, page, size);
        return ResponseEntity.ok(new ResponseDto<>(profiles, String.valueOf(HttpStatus.OK.value())));
    }

    // ==================== 6. POLICY EFFECTIVENESS ====================

    @GetMapping("/policy-effectiveness")
    @Operation(summary = "Policy effectiveness",
            description = "Policy outcome metrics: block/warn counts, warning acknowledge rate, top blocked/warned extensions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Policy effectiveness retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<ExtensionDashboardDTO.PolicyEffectiveness>> getPolicyEffectiveness(
            HttpServletRequest request,
            @Parameter(description = "Number of days to look back (default 30)") @RequestParam(defaultValue = "30") int days) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        ExtensionDashboardDTO.PolicyEffectiveness effectiveness = dashboardService.getPolicyEffectiveness(tenantId, days);
        return ResponseEntity.ok(new ResponseDto<>(effectiveness, String.valueOf(HttpStatus.OK.value())));
    }

    // ==================== 7. BULK ACTION ====================

    @PostMapping("/bulk-action")
    @Operation(summary = "Bulk action",
            description = "Apply policy action to multiple extensions: WHITELIST, BLACKLIST, BLOCK, WARN, or ALLOW")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bulk action executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid action or missing extension IDs")
    })
    public ResponseEntity<ResponseDto<ExtensionDashboardDTO.BulkActionResult>> executeBulkAction(
            HttpServletRequest request,
            @RequestBody ExtensionDashboardDTO.BulkActionRequest bulkRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        if (bulkRequest.getExtensionIds() == null || bulkRequest.getExtensionIds().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "extensionIds is required", "400"));
        }
        if (bulkRequest.getAction() == null || bulkRequest.getAction().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "action is required", "400"));
        }

        ExtensionDashboardDTO.BulkActionResult result = dashboardService.executeBulkAction(tenantId, bulkRequest);
        return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
    }
}
