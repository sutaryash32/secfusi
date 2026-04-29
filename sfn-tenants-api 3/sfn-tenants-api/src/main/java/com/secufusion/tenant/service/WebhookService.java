package com.secufusion.tenant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.tenant.dto.*;
import com.secufusion.tenant.entity.WebhookConfig;
import com.secufusion.tenant.entity.WebhookDeliveryLog;
import com.secufusion.tenant.entity.WebhookType;
import com.secufusion.tenant.exception.ResourceConflictException;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.WebhookConfigRepository;
import com.secufusion.tenant.repository.WebhookDeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter INSTANT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    private static final int MAX_RETRIES = 3;

    @Transactional
    public WebhookConfigDTO createWebhook(String tenantId, String userId,
                                           CreateWebhookConfigRequest request) {
        log.info("createWebhook - tenantId={} name={}", tenantId, request.getName());

        if (webhookConfigRepository.existsByFkTenantIdAndName(tenantId, request.getName())) {
            throw new ResourceConflictException("Webhook config with name '" + request.getName() + "' already exists");
        }

        WebhookConfig config = WebhookConfig.builder()
                .fkTenantId(tenantId)
                .name(request.getName())
                .webhookType(parseWebhookType(request.getWebhookType()))
                .url(request.getUrl())
                .headers(request.getHeaders())
                .secret(request.getSecret())
                .categories(request.getCategories())
                .severities(request.getSeverities())
                .isActive(true)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        config = webhookConfigRepository.save(config);
        log.info("createWebhook - success. configId={}", config.getPkWebhookConfigId());
        return convertToDTO(config);
    }

    @Transactional(readOnly = true)
    public List<WebhookConfigDTO> listWebhooks(String tenantId, boolean activeOnly) {
        log.debug("listWebhooks - tenantId={} activeOnly={}", tenantId, activeOnly);

        List<WebhookConfig> configs = activeOnly
                ? webhookConfigRepository.findByFkTenantIdAndIsActiveTrueOrderByNameAsc(tenantId)
                : webhookConfigRepository.findByFkTenantIdOrderByNameAsc(tenantId);

        return configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Async
    @Transactional
    public void deliverToWebhooks(NotificationEvent event) {
        log.debug("[WEBHOOK] deliverToWebhooks - type={} tenant={}", event.getType(), event.getTenantId());

        List<WebhookConfig> activeConfigs = webhookConfigRepository
                .findByFkTenantIdAndIsActiveTrueOrderByNameAsc(event.getTenantId());

        if (activeConfigs.isEmpty()) {
            log.debug("[WEBHOOK] No active webhooks for tenant={}", event.getTenantId());
            return;
        }

        String category = mapTypeToCategory(event.getType());
        String severity = event.getSeverity() != null ? event.getSeverity() : "INFO";

        for (WebhookConfig config : activeConfigs) {
            if (config.getCategories() != null && !config.getCategories().isEmpty()) {
                if (category == null || !config.getCategories().contains(category)) {
                    continue;
                }
            }

            if (config.getSeverities() != null && !config.getSeverities().isEmpty()) {
                if (!config.getSeverities().contains(severity)) {
                    continue;
                }
            }

            deliverWithRetry(config, event, 1);
        }
    }

    private void deliverWithRetry(WebhookConfig config, NotificationEvent event, int attempt) {
        String payload = formatPayload(config.getWebhookType(), event);
        HttpHeaders headers = buildHeaders(config);

        WebhookDeliveryLog deliveryLog = WebhookDeliveryLog.builder()
                .fkWebhookConfigId(config.getPkWebhookConfigId())
                .fkTenantId(config.getFkTenantId())
                .eventType(event.getType())
                .attemptCount(attempt)
                .requestBody(truncate(payload, 5000))
                .build();

        try {
            HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getUrl(), HttpMethod.POST, requestEntity, String.class);

            deliveryLog.setStatus("SUCCESS");
            deliveryLog.setHttpStatus(response.getStatusCode().value());
            deliveryLog.setResponseBody(truncate(response.getBody(), 5000));

            log.info("[WEBHOOK] Delivery success. config='{}' type={} httpStatus={}",
                    config.getName(), event.getType(), response.getStatusCode().value());

        } catch (Exception e) {
            log.error("[WEBHOOK] Delivery failed. config='{}' type={} attempt={}/{}",
                    config.getName(), event.getType(), attempt, MAX_RETRIES, e);

            deliveryLog.setStatus(attempt < MAX_RETRIES ? "RETRYING" : "FAILED");
            deliveryLog.setErrorMessage(truncate(e.getMessage(), 2000));

            if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                deliveryLog.setHttpStatus(((org.springframework.web.client.HttpStatusCodeException) e)
                        .getStatusCode().value());
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                deliveryLogRepository.save(deliveryLog);
                deliverWithRetry(config, event, attempt + 1);
                return;
            }
        }

        deliveryLogRepository.save(deliveryLog);
    }

    private String formatPayload(WebhookType type, NotificationEvent event) {
        try {
            return switch (type) {
                case SLACK -> formatSlackPayload(event);
                case TEAMS -> formatTeamsPayload(event);
                case PAGERDUTY -> formatPagerDutyPayload(event);
                case CUSTOM -> formatCustomPayload(event);
            };
        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to format payload for type={}", type, e);
            return formatCustomPayload(event);
        }
    }

    private String formatSlackPayload(NotificationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", event.getTitle());
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("color", severityToColor(event.getSeverity()));
        attachment.put("text", event.getMessage());
        payload.put("attachments", List.of(attachment));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String formatTeamsPayload(NotificationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("@type", "MessageCard");
        payload.put("title", event.getTitle());
        payload.put("text", event.getMessage());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String formatPagerDutyPayload(NotificationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_action", "trigger");
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("summary", event.getTitle());
        eventPayload.put("severity", event.getSeverity() != null ? event.getSeverity().toLowerCase() : "info");
        payload.put("payload", eventPayload);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String formatCustomPayload(NotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{}";
        }
    }

    private HttpHeaders buildHeaders(WebhookConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(headers::add);
        }
        return headers;
    }

    private String severityToColor(String severity) {
        if (severity == null) return "#808080";
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#FF0000";
            case "HIGH" -> "#FF6600";
            case "MEDIUM" -> "#FFCC00";
            case "LOW" -> "#00CC00";
            default -> "#808080";
        };
    }

    private String mapTypeToCategory(String type) {
        if (type == null) return null;
        if (type.startsWith("INCIDENT_")) return "INCIDENT";
        if (type.startsWith("LOGIN_") || type.startsWith("ACCOUNT_")) return "SECURITY";
        if (type.startsWith("USER_") || type.startsWith("ROLE_")) return "USER_MANAGEMENT";
        return null;
    }

    private WebhookType parseWebhookType(String type) {
        if (type == null) return WebhookType.CUSTOM;
        try {
            return WebhookType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WebhookType.CUSTOM;
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }

    private WebhookConfigDTO convertToDTO(WebhookConfig config) {
        return WebhookConfigDTO.builder()
                .webhookConfigId(config.getPkWebhookConfigId())
                .name(config.getName())
                .webhookType(config.getWebhookType().name())
                .url(config.getUrl())
                .headers(config.getHeaders())
                .hasSecret(config.getSecret() != null && !config.getSecret().isEmpty())
                .categories(config.getCategories())
                .severities(config.getSeverities())
                .isActive(config.getIsActive())
                .createdAt(formatInstant(config.getCreatedAt()))
                .updatedAt(formatInstant(config.getUpdatedAt()))
                .build();
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryLogDTO> getDeliveryLogs(String tenantId, String configId, Pageable pageable) {
        log.debug("getDeliveryLogs - tenantId={} configId={}", tenantId, configId);

        Page<WebhookDeliveryLog> logs = (configId != null)
                ? deliveryLogRepository.findByFkWebhookConfigIdOrderByCreatedAtDesc(configId, pageable)
                : deliveryLogRepository.findByFkTenantIdOrderByCreatedAtDesc(tenantId, pageable);

        return logs.map(this::convertToLogDTO);
    }

    private WebhookDeliveryLogDTO convertToLogDTO(WebhookDeliveryLog log) {
        return WebhookDeliveryLogDTO.builder()
                .deliveryLogId(log.getPkDeliveryLogId())
                .webhookConfigId(log.getFkWebhookConfigId())
                .eventType(log.getEventType())
                .status(log.getStatus())
                .httpStatus(log.getHttpStatus())
                .attemptCount(log.getAttemptCount())
                .errorMessage(log.getErrorMessage())
                .createdAt(formatInstant(log.getCreatedAt()))
                .build();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? INSTANT_FORMATTER.format(instant) : null;
    }
}
