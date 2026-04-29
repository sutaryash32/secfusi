package com.secufusion.events.controller;

import com.secufusion.events.dto.CreateEscalationRuleRequest;
import com.secufusion.events.dto.EscalationRuleDTO;
import com.secufusion.events.dto.ResponseDto;
import com.secufusion.events.service.EscalationService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/events/escalation-rules")
@Tag(name = "Escalation Rules", description = "Configure automatic incident escalation rules")
@RequiredArgsConstructor
public class EscalationController {

    private final EscalationService escalationService;
    private final JwtUtl jwtUtl;

    @PostMapping
    @Operation(summary = "Create escalation rule",
            description = "Create a new escalation rule to auto-escalate incidents based on priority, unassigned time, or unresolved time")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rule created successfully"),
            @ApiResponse(responseCode = "409", description = "Rule with same name already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<EscalationRuleDTO>> createRule(
            HttpServletRequest request,
            @Valid @RequestBody CreateEscalationRuleRequest createRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            EscalationRuleDTO rule = escalationService.createRule(tenantId, createRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseDto<>(rule, String.valueOf(HttpStatus.CREATED.value())));
        } catch (Exception ex) {
            log.error("createRule - error. tenantId={}", tenantId, ex);
            HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("already exists")
                    ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "List escalation rules", description = "Get all active escalation rules for the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rules retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<EscalationRuleDTO>>> listRules(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            List<EscalationRuleDTO> rules = escalationService.listRules(tenantId);
            return ResponseEntity.ok(new ResponseDto<>(rules, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("listRules - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "Update escalation rule", description = "Update an existing escalation rule")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule updated successfully"),
            @ApiResponse(responseCode = "404", description = "Rule not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<EscalationRuleDTO>> updateRule(
            HttpServletRequest request,
            @PathVariable String ruleId,
            @Valid @RequestBody CreateEscalationRuleRequest updateRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            EscalationRuleDTO rule = escalationService.updateRule(tenantId, ruleId, updateRequest);
            return ResponseEntity.ok(new ResponseDto<>(rule, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("updateRule - error. tenantId={} ruleId={}", tenantId, ruleId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "Deactivate escalation rule", description = "Soft-delete an escalation rule by setting isActive to false")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Rule not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<String>> deactivateRule(
            HttpServletRequest request,
            @PathVariable String ruleId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            escalationService.deactivateRule(tenantId, ruleId);
            return ResponseEntity.ok(new ResponseDto<>("Escalation rule deactivated successfully",
                    String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("deactivateRule - error. tenantId={} ruleId={}", tenantId, ruleId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }
}
