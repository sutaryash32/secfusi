package com.secufusion.events.service;

import com.secufusion.events.dto.CreateEscalationRuleRequest;
import com.secufusion.events.dto.EscalationRuleDTO;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.ResourceConflictException;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.event.IncidentNotificationEvent;
import com.secufusion.events.repository.EscalationRuleRepository;
import com.secufusion.events.repository.IncidentActivityRepository;
import com.secufusion.events.repository.IncidentRepository;
import com.secufusion.events.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRuleRepository escalationRuleRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentActivityRepository incidentActivityRepository;
    private final TenantRepository tenantRepository;
    private final NotificationDispatchService notificationDispatchService;

    private static final DateTimeFormatter INSTANT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    // ==================== CRUD ====================

    @Transactional
    public EscalationRuleDTO createRule(String tenantId, CreateEscalationRuleRequest request) {
        log.info("createRule - tenantId={} name={}", tenantId, request.getName());

        Tenant tenant = tenantRepository.findByTenantID(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (escalationRuleRepository.existsByTenant_TenantIDAndName(tenantId, request.getName())) {
            throw new ResourceConflictException("Escalation rule with name '" + request.getName() + "' already exists");
        }

        EscalationRule rule = EscalationRule.builder()
                .tenant(tenant)
                .name(request.getName())
                .description(request.getDescription())
                .triggerPriority(parsePriority(request.getTriggerPriority()))
                .unassignedMinutes(request.getUnassignedMinutes())
                .unresolvedHours(request.getUnresolvedHours())
                .escalateToPriority(parsePriority(request.getEscalateToPriority()))
                .notifyRole(request.getNotifyRole())
                .build();

        rule = escalationRuleRepository.save(rule);
        log.info("createRule - success. ruleId={}", rule.getPkEscalationRuleId());
        return convertToDTO(rule);
    }

    @Transactional(readOnly = true)
    public List<EscalationRuleDTO> listRules(String tenantId) {
        log.debug("listRules - tenantId={}", tenantId);
        return escalationRuleRepository.findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(tenantId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public EscalationRuleDTO updateRule(String tenantId, String ruleId, CreateEscalationRuleRequest request) {
        log.info("updateRule - tenantId={} ruleId={}", tenantId, ruleId);

        EscalationRule rule = escalationRuleRepository
                .findByPkEscalationRuleIdAndTenant_TenantID(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Escalation rule not found: " + ruleId));

        if (request.getName() != null) rule.setName(request.getName());
        if (request.getDescription() != null) rule.setDescription(request.getDescription());
        if (request.getTriggerPriority() != null) rule.setTriggerPriority(parsePriority(request.getTriggerPriority()));
        if (request.getUnassignedMinutes() != null) rule.setUnassignedMinutes(request.getUnassignedMinutes());
        if (request.getUnresolvedHours() != null) rule.setUnresolvedHours(request.getUnresolvedHours());
        if (request.getEscalateToPriority() != null) rule.setEscalateToPriority(parsePriority(request.getEscalateToPriority()));
        if (request.getNotifyRole() != null) rule.setNotifyRole(request.getNotifyRole());

        rule = escalationRuleRepository.save(rule);
        log.info("updateRule - success. ruleId={}", ruleId);
        return convertToDTO(rule);
    }

    @Transactional
    public void deactivateRule(String tenantId, String ruleId) {
        log.info("deactivateRule - tenantId={} ruleId={}", tenantId, ruleId);

        EscalationRule rule = escalationRuleRepository
                .findByPkEscalationRuleIdAndTenant_TenantID(ruleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Escalation rule not found: " + ruleId));

        rule.setIsActive(false);
        escalationRuleRepository.save(rule);
        log.info("deactivateRule - success. ruleId={}", ruleId);
    }

    // ==================== ESCALATION PROCESSING ====================

    @Transactional
    public void processEscalations() {
        log.debug("[ESCALATION] Starting escalation check");

        List<EscalationRule> activeRules = escalationRuleRepository.findByIsActiveTrue();
        if (activeRules.isEmpty()) {
            log.debug("[ESCALATION] No active escalation rules found");
            return;
        }

        // Group rules by tenant
        Map<String, List<EscalationRule>> rulesByTenant = activeRules.stream()
                .collect(Collectors.groupingBy(r -> r.getTenant().getTenantID()));

        int totalEscalated = 0;

        for (Map.Entry<String, List<EscalationRule>> entry : rulesByTenant.entrySet()) {
            String tenantId = entry.getKey();
            List<EscalationRule> tenantRules = entry.getValue();

            for (EscalationRule rule : tenantRules) {
                int escalated = processRule(tenantId, rule);
                totalEscalated += escalated;
            }
        }

        if (totalEscalated > 0) {
            log.info("[ESCALATION] Completed. Total incidents escalated: {}", totalEscalated);
        } else {
            log.debug("[ESCALATION] Completed. No incidents escalated");
        }
    }

    private int processRule(String tenantId, EscalationRule rule) {
        Instant now = Instant.now();
        String priorityFilter = rule.getTriggerPriority() != null ? rule.getTriggerPriority().name() : null;

        // Calculate thresholds
        Instant unassignedThreshold = rule.getUnassignedMinutes() != null
                ? now.minusSeconds(rule.getUnassignedMinutes() * 60L)
                : now; // if not set, don't match on unassigned

        Instant unresolvedThreshold = rule.getUnresolvedHours() != null
                ? now.minusSeconds(rule.getUnresolvedHours() * 3600L)
                : now; // if not set, don't match on unresolved

        List<Incident> candidates = incidentRepository.findEscalationCandidates(
                tenantId, priorityFilter, unassignedThreshold, unresolvedThreshold);

        int escalated = 0;

        for (Incident incident : candidates) {
            try {
                escalateIncident(incident, rule, tenantId);
                escalated++;
            } catch (Exception e) {
                log.error("[ESCALATION] Failed to escalate incident {}. rule={}",
                        incident.getPkIncidentId(), rule.getPkEscalationRuleId(), e);
            }
        }

        if (escalated > 0) {
            log.info("[ESCALATION] Rule '{}' escalated {} incident(s) for tenant={}",
                    rule.getName(), escalated, tenantId);
        }

        return escalated;
    }

    private void escalateIncident(Incident incident, EscalationRule rule, String tenantId) {
        String oldPriority = incident.getPriority().name();

        // Change priority if escalateToPriority is set
        if (rule.getEscalateToPriority() != null) {
            incident.setPriority(rule.getEscalateToPriority());
        }

        // Mark as escalated to prevent re-escalation
        incident.setEscalatedAt(Instant.now());
        incidentRepository.save(incident);

        // Record activity
        String description = "Auto-escalated by rule '" + rule.getName() + "'";
        if (rule.getEscalateToPriority() != null) {
            description += ". Priority changed from " + oldPriority + " to " + rule.getEscalateToPriority().name();
        }

        IncidentActivity activity = IncidentActivity.builder()
                .incident(incident)
                .tenantId(tenantId)
                .action(IncidentActivityAction.ESCALATED)
                .description(description)
                .performedBy("SYSTEM")
                .performedByName("Escalation Engine")
                .performedAt(Instant.now())
                .oldValue(oldPriority)
                .newValue(rule.getEscalateToPriority() != null ? rule.getEscalateToPriority().name() : oldPriority)
                .build();
        incidentActivityRepository.save(activity);

        // Publish notification with targetRole from rule
        publishEscalationNotification(incident, rule, tenantId, oldPriority);

        log.info("[ESCALATION] Incident {} escalated. rule='{}' priority={}->{} notifyRole={}",
                incident.getIncidentNumber(), rule.getName(), oldPriority,
                rule.getEscalateToPriority() != null ? rule.getEscalateToPriority().name() : oldPriority,
                rule.getNotifyRole());
    }

    private void publishEscalationNotification(Incident incident, EscalationRule rule,
                                                String tenantId, String oldPriority) {
        try {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("incidentId", incident.getPkIncidentId());
            metadata.put("incidentNumber", incident.getIncidentNumber());
            metadata.put("priority", incident.getPriority().name());
            metadata.put("oldPriority", oldPriority);
            metadata.put("escalationRule", rule.getName());

            IncidentNotificationEvent event = new IncidentNotificationEvent(
                    "INCIDENT_ESCALATED",
                    tenantId,
                    "SYSTEM",
                    incident.getPkIncidentId(),
                    incident.getIncidentNumber(),
                    incident.getPriority().name(),
                    incident.getCategory() != null ? incident.getCategory().name() : null,
                    mapPriorityToSeverity(incident.getPriority()),
                    "Incident " + incident.getIncidentNumber() + " Escalated",
                    "Incident '" + incident.getTitle() + "' has been auto-escalated by rule '"
                            + rule.getName() + "'"
                            + (rule.getEscalateToPriority() != null
                                ? ". Priority: " + oldPriority + " -> " + rule.getEscalateToPriority().name()
                                : ""),
                    null,
                    metadata);

            notificationDispatchService.dispatch(event);
        } catch (Exception e) {
            log.error("[ESCALATION] Failed to publish notification for incident {}",
                    incident.getPkIncidentId(), e);
        }
    }

    // ==================== HELPERS ====================

    private IncidentPriority parsePriority(String priority) {
        if (priority == null) return null;
        try {
            return IncidentPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String mapPriorityToSeverity(IncidentPriority priority) {
        if (priority == null) return "INFO";
        return switch (priority) {
            case P1_CRITICAL -> "CRITICAL";
            case P2_HIGH -> "HIGH";
            case P3_MEDIUM -> "MEDIUM";
            case P4_LOW -> "LOW";
        };
    }

    private EscalationRuleDTO convertToDTO(EscalationRule rule) {
        return EscalationRuleDTO.builder()
                .ruleId(rule.getPkEscalationRuleId())
                .name(rule.getName())
                .description(rule.getDescription())
                .triggerPriority(rule.getTriggerPriority() != null ? rule.getTriggerPriority().name() : null)
                .unassignedMinutes(rule.getUnassignedMinutes())
                .unresolvedHours(rule.getUnresolvedHours())
                .escalateToPriority(rule.getEscalateToPriority() != null ? rule.getEscalateToPriority().name() : null)
                .notifyRole(rule.getNotifyRole())
                .isActive(rule.getIsActive())
                .createdAt(rule.getCreatedAt() != null ? INSTANT_FORMATTER.format(rule.getCreatedAt()) : null)
                .updatedAt(rule.getUpdatedAt() != null ? INSTANT_FORMATTER.format(rule.getUpdatedAt()) : null)
                .build();
    }
}
