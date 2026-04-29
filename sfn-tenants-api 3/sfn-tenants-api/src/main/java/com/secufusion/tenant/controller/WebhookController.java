package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.CreateWebhookConfigRequest;
import com.secufusion.tenant.dto.ResponseDto;
import com.secufusion.tenant.dto.WebhookConfigDTO;
import com.secufusion.tenant.dto.WebhookDeliveryLogDTO;
import com.secufusion.tenant.service.WebhookService;
import com.secufusion.tenant.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tenants/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Configure outgoing webhooks for Slack, Teams, PagerDuty, and custom integrations")
public class WebhookController {

    private final WebhookService webhookService;
    private final JwtUtl jwtUtl;

    @PostMapping
    @Operation(summary = "Create webhook configuration")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Webhook created"),
            @ApiResponse(responseCode = "409", description = "Webhook with same name exists")
    })
    public ResponseEntity<ResponseDto<WebhookConfigDTO>> createWebhook(
            HttpServletRequest request,
            @Valid @RequestBody CreateWebhookConfigRequest createRequest) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        String userId = jwtUtl.getUserId(request);

        try {
            WebhookConfigDTO webhook = webhookService.createWebhook(tenantId, userId, createRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseDto<>(webhook, String.valueOf(HttpStatus.CREATED.value())));
        } catch (Exception ex) {
            log.error("createWebhook error: tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(ex.getMessage(), "500"));
        }
    }

    @GetMapping
    @Operation(summary = "List webhook configurations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhooks retrieved")
    })
    public ResponseEntity<ResponseDto<List<WebhookConfigDTO>>> listWebhooks(
            HttpServletRequest request,
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            List<WebhookConfigDTO> webhooks = webhookService.listWebhooks(tenantId, activeOnly);
            return ResponseEntity.ok(new ResponseDto<>(webhooks, "200"));
        } catch (Exception ex) {
            log.error("listWebhooks error: tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(ex.getMessage(), "500"));
        }
    }

    @GetMapping("/logs")
    @Operation(summary = "Get webhook delivery logs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logs retrieved")
    })
    public ResponseEntity<ResponseDto<Page<WebhookDeliveryLogDTO>>> getDeliveryLogs(
            HttpServletRequest request,
            @RequestParam(required = false) String configId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();

        try {
            Page<WebhookDeliveryLogDTO> logs = webhookService.getDeliveryLogs(
                    tenantId, configId, PageRequest.of(page, size));
            return ResponseEntity.ok(new ResponseDto<>(logs, "200"));
        } catch (Exception ex) {
            log.error("getDeliveryLogs error: tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(ex.getMessage(), "500"));
        }
    }
}
