package com.secufusion.events.controller;

import com.secufusion.events.dto.*;
import com.secufusion.events.service.PlaybookService;
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
@RequiredArgsConstructor
@Tag(name = "Playbooks", description = "Incident playbook template management and step tracking")
public class PlaybookController {

    private final PlaybookService playbookService;
    private final JwtUtl jwtUtl;

    // ==================== TEMPLATE CRUD ====================

    @PostMapping("/api/events/playbooks")
    @Operation(summary = "Create playbook template")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Template created"),
            @ApiResponse(responseCode = "409", description = "Duplicate name"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<PlaybookTemplateDTO>> createTemplate(
            HttpServletRequest request,
            @Valid @RequestBody CreatePlaybookTemplateRequest createRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            PlaybookTemplateDTO result = playbookService.createTemplate(tenantId, createRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseDto<>(result, String.valueOf(HttpStatus.CREATED.value())));
        } catch (Exception ex) {
            log.error("createTemplate - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping("/api/events/playbooks")
    @Operation(summary = "List playbook templates")
    public ResponseEntity<ResponseDto<List<PlaybookTemplateDTO>>> listTemplates(
            HttpServletRequest request,
            @RequestParam(required = false) String category) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            List<PlaybookTemplateDTO> result = playbookService.listTemplates(tenantId, category);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("listTemplates - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping("/api/events/playbooks/{templateId}")
    @Operation(summary = "Get playbook template detail")
    public ResponseEntity<ResponseDto<PlaybookTemplateDTO>> getTemplate(
            HttpServletRequest request,
            @PathVariable String templateId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            PlaybookTemplateDTO result = playbookService.getTemplate(tenantId, templateId);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getTemplate - error. tenantId={} templateId={}", tenantId, templateId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PutMapping("/api/events/playbooks/{templateId}")
    @Operation(summary = "Update playbook template")
    public ResponseEntity<ResponseDto<PlaybookTemplateDTO>> updateTemplate(
            HttpServletRequest request,
            @PathVariable String templateId,
            @Valid @RequestBody CreatePlaybookTemplateRequest updateRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            PlaybookTemplateDTO result = playbookService.updateTemplate(tenantId, templateId, updateRequest);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("updateTemplate - error. tenantId={} templateId={}", tenantId, templateId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @DeleteMapping("/api/events/playbooks/{templateId}")
    @Operation(summary = "Deactivate playbook template")
    public ResponseEntity<ResponseDto<String>> deleteTemplate(
            HttpServletRequest request,
            @PathVariable String templateId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            playbookService.deactivateTemplate(tenantId, templateId);
            return ResponseEntity.ok(new ResponseDto<>("Template deactivated", String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("deleteTemplate - error. tenantId={} templateId={}", tenantId, templateId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== INCIDENT PLAYBOOK MANAGEMENT ====================

    @PostMapping("/api/events/incidents/{incidentId}/playbooks")
    @Operation(summary = "Attach playbook to incident")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Playbook attached"),
            @ApiResponse(responseCode = "409", description = "Playbook already attached"),
            @ApiResponse(responseCode = "404", description = "Incident or template not found")
    })
    public ResponseEntity<ResponseDto<IncidentPlaybookDTO>> attachPlaybook(
            HttpServletRequest request,
            @PathVariable String incidentId,
            @Valid @RequestBody AttachPlaybookRequest attachRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            IncidentPlaybookDTO result = playbookService.attachPlaybook(
                    tenantId, userId, userName, incidentId, attachRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseDto<>(result, String.valueOf(HttpStatus.CREATED.value())));
        } catch (Exception ex) {
            log.error("attachPlaybook - error. tenantId={} incidentId={}", tenantId, incidentId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @GetMapping("/api/events/incidents/{incidentId}/playbooks")
    @Operation(summary = "Get playbooks attached to incident")
    public ResponseEntity<ResponseDto<List<IncidentPlaybookDTO>>> getIncidentPlaybooks(
            HttpServletRequest request,
            @PathVariable String incidentId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            List<IncidentPlaybookDTO> result = playbookService.getIncidentPlaybooks(tenantId, incidentId);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getIncidentPlaybooks - error. tenantId={} incidentId={}", tenantId, incidentId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== STEP COMPLETION ====================

    @PatchMapping("/api/events/incidents/{incidentId}/playbooks/{playbookId}/steps/{stepId}/complete")
    @Operation(summary = "Complete a playbook step")
    public ResponseEntity<ResponseDto<IncidentPlaybookStepDTO>> completeStep(
            HttpServletRequest request,
            @PathVariable String incidentId,
            @PathVariable String playbookId,
            @PathVariable String stepId,
            @RequestBody(required = false) CompleteStepRequest completeRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            IncidentPlaybookStepDTO result = playbookService.completeStep(
                    tenantId, userId, userName, incidentId, playbookId, stepId, completeRequest);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("completeStep - error. playbookId={} stepId={}", playbookId, stepId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    @PatchMapping("/api/events/incidents/{incidentId}/playbooks/{playbookId}/steps/{stepId}/uncomplete")
    @Operation(summary = "Revert a completed playbook step")
    public ResponseEntity<ResponseDto<IncidentPlaybookStepDTO>> uncompleteStep(
            HttpServletRequest request,
            @PathVariable String incidentId,
            @PathVariable String playbookId,
            @PathVariable String stepId) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);
        String userName = jwtUtl.getUsername(request);

        try {
            IncidentPlaybookStepDTO result = playbookService.uncompleteStep(
                    tenantId, userId, userName, incidentId, playbookId, stepId);
            return ResponseEntity.ok(new ResponseDto<>(result, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("uncompleteStep - error. playbookId={} stepId={}", playbookId, stepId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }
}
