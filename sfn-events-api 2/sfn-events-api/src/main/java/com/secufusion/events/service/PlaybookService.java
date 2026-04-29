package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.ResourceConflictException;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaybookService {

    private final PlaybookTemplateRepository templateRepository;
    private final IncidentPlaybookRepository playbookRepository;
    private final IncidentPlaybookStepRepository stepRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentActivityRepository activityRepository;
    private final TenantRepository tenantRepository;

    private static final DateTimeFormatter INSTANT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    // ==================== TEMPLATE CRUD ====================

    @Transactional
    public PlaybookTemplateDTO createTemplate(String tenantId, CreatePlaybookTemplateRequest request) {
        log.info("createTemplate - tenantId={} name={}", tenantId, request.getName());

        if (templateRepository.existsByTenant_TenantIDAndName(tenantId, request.getName())) {
            throw new ResourceConflictException("Playbook template with name '" + request.getName() + "' already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        IncidentCategory category = null;
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            category = IncidentCategory.valueOf(request.getCategory());
        }

        PlaybookTemplate template = PlaybookTemplate.builder()
                .tenant(tenant)
                .name(request.getName())
                .description(request.getDescription())
                .category(category)
                .steps(request.getSteps())
                .isActive(true)
                .build();

        template = templateRepository.save(template);
        log.info("createTemplate - success. templateId={}", template.getPkPlaybookTemplateId());
        return convertToTemplateDTO(template);
    }

    @Transactional(readOnly = true)
    public List<PlaybookTemplateDTO> listTemplates(String tenantId, String category) {
        List<PlaybookTemplate> templates;
        if (category != null && !category.isBlank()) {
            templates = templateRepository.findByTenant_TenantIDAndCategoryAndIsActiveTrueOrderByNameAsc(
                    tenantId, IncidentCategory.valueOf(category));
        } else {
            templates = templateRepository.findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(tenantId);
        }
        return templates.stream().map(this::convertToTemplateDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlaybookTemplateDTO getTemplate(String tenantId, String templateId) {
        PlaybookTemplate template = findTemplateOrThrow(templateId, tenantId);
        return convertToTemplateDTO(template);
    }

    @Transactional
    public PlaybookTemplateDTO updateTemplate(String tenantId, String templateId,
                                               CreatePlaybookTemplateRequest request) {
        PlaybookTemplate template = findTemplateOrThrow(templateId, tenantId);

        if (request.getName() != null) template.setName(request.getName());
        if (request.getDescription() != null) template.setDescription(request.getDescription());
        if (request.getSteps() != null) template.setSteps(request.getSteps());
        if (request.getCategory() != null) {
            template.setCategory(request.getCategory().isBlank() ? null
                    : IncidentCategory.valueOf(request.getCategory()));
        }

        template = templateRepository.save(template);
        return convertToTemplateDTO(template);
    }

    @Transactional
    public void deactivateTemplate(String tenantId, String templateId) {
        PlaybookTemplate template = findTemplateOrThrow(templateId, tenantId);
        template.setIsActive(false);
        templateRepository.save(template);
        log.info("deactivateTemplate - templateId={}", templateId);
    }

    // ==================== ATTACH PLAYBOOK TO INCIDENT ====================

    @Transactional
    public IncidentPlaybookDTO attachPlaybook(String tenantId, String userId, String userName,
                                                String incidentId, AttachPlaybookRequest request) {
        log.info("attachPlaybook - tenantId={} incidentId={} templateId={}",
                tenantId, incidentId, request.getTemplateId());

        Incident incident = incidentRepository.findByPkIncidentIdAndTenant_TenantID(incidentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        PlaybookTemplate template = findTemplateOrThrow(request.getTemplateId(), tenantId);

        if (playbookRepository.existsByIncident_PkIncidentIdAndPlaybookTemplate_PkPlaybookTemplateId(
                incidentId, request.getTemplateId())) {
            throw new ResourceConflictException("Playbook already attached to this incident");
        }

        // Create incident playbook
        IncidentPlaybook playbook = IncidentPlaybook.builder()
                .incident(incident)
                .playbookTemplate(template)
                .tenantId(tenantId)
                .totalSteps(template.getSteps().size())
                .completedSteps(0)
                .isComplete(false)
                .build();
        playbook = playbookRepository.save(playbook);

        // Create step records from template
        List<IncidentPlaybookStep> steps = new ArrayList<>();
        for (int i = 0; i < template.getSteps().size(); i++) {
            Map<String, Object> stepDef = template.getSteps().get(i);
            IncidentPlaybookStep step = IncidentPlaybookStep.builder()
                    .incidentPlaybook(playbook)
                    .tenantId(tenantId)
                    .stepNumber(i + 1)
                    .title(String.valueOf(stepDef.getOrDefault("title", "Step " + (i + 1))))
                    .description(stepDef.get("description") != null ? String.valueOf(stepDef.get("description")) : null)
                    .isRequired(Boolean.parseBoolean(String.valueOf(stepDef.getOrDefault("isRequired", "false"))))
                    .isCompleted(false)
                    .build();
            steps.add(step);
        }
        stepRepository.saveAll(steps);

        // Record activity
        recordActivity(incident, tenantId, IncidentActivityAction.PLAYBOOK_ATTACHED,
                "Playbook '" + template.getName() + "' attached (" + steps.size() + " steps)",
                userId, userName);

        log.info("attachPlaybook - success. playbookId={} steps={}", playbook.getPkIncidentPlaybookId(), steps.size());
        return convertToPlaybookDTO(playbook, steps);
    }

    @Transactional(readOnly = true)
    public List<IncidentPlaybookDTO> getIncidentPlaybooks(String tenantId, String incidentId) {
        List<IncidentPlaybook> playbooks = playbookRepository
                .findByIncident_PkIncidentIdAndTenantIdOrderByCreatedAtDesc(incidentId, tenantId);

        return playbooks.stream().map(pb -> {
            List<IncidentPlaybookStep> steps = stepRepository
                    .findByIncidentPlaybook_PkIncidentPlaybookIdAndTenantIdOrderByStepNumberAsc(
                            pb.getPkIncidentPlaybookId(), tenantId);
            return convertToPlaybookDTO(pb, steps);
        }).collect(Collectors.toList());
    }

    // ==================== STEP COMPLETION ====================

    @Transactional
    public IncidentPlaybookStepDTO completeStep(String tenantId, String userId, String userName,
                                                  String incidentId, String playbookId, String stepId,
                                                  CompleteStepRequest request) {
        log.info("completeStep - tenantId={} playbookId={} stepId={}", tenantId, playbookId, stepId);

        IncidentPlaybook playbook = playbookRepository.findByPkIncidentPlaybookIdAndTenantId(playbookId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Playbook not found: " + playbookId));

        IncidentPlaybookStep step = stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(stepId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Step not found: " + stepId));

        if (Boolean.TRUE.equals(step.getIsCompleted())) {
            throw new IllegalStateException("Step is already completed");
        }

        step.setIsCompleted(true);
        step.setCompletedBy(userId);
        step.setCompletedByName(userName);
        step.setCompletedAt(Instant.now());
        if (request != null && request.getNotes() != null) {
            step.setNotes(request.getNotes());
        }
        step = stepRepository.save(step);

        // Update playbook counters
        long completedCount = stepRepository.countByIncidentPlaybook_PkIncidentPlaybookIdAndIsCompletedTrue(playbookId);
        playbook.setCompletedSteps((int) completedCount);

        boolean allComplete = completedCount >= playbook.getTotalSteps();
        playbook.setIsComplete(allComplete);
        playbookRepository.save(playbook);

        // Record activity
        Incident incident = playbook.getIncident();
        recordActivity(incident, tenantId, IncidentActivityAction.PLAYBOOK_STEP_COMPLETED,
                "Step " + step.getStepNumber() + " '" + step.getTitle() + "' completed"
                        + (step.getNotes() != null ? " — " + step.getNotes() : ""),
                userId, userName);

        if (allComplete) {
            recordActivity(incident, tenantId, IncidentActivityAction.PLAYBOOK_COMPLETED,
                    "Playbook '" + playbook.getPlaybookTemplate().getName() + "' completed ("
                            + playbook.getTotalSteps() + "/" + playbook.getTotalSteps() + " steps)",
                    userId, userName);
        }

        return convertToStepDTO(step);
    }

    @Transactional
    public IncidentPlaybookStepDTO uncompleteStep(String tenantId, String userId, String userName,
                                                     String incidentId, String playbookId, String stepId) {
        IncidentPlaybook playbook = playbookRepository.findByPkIncidentPlaybookIdAndTenantId(playbookId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Playbook not found: " + playbookId));

        IncidentPlaybookStep step = stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(stepId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Step not found: " + stepId));

        if (Boolean.FALSE.equals(step.getIsCompleted())) {
            throw new IllegalStateException("Step is not completed");
        }

        step.setIsCompleted(false);
        step.setCompletedBy(null);
        step.setCompletedByName(null);
        step.setCompletedAt(null);
        step.setNotes(null);
        step = stepRepository.save(step);

        // Update counters
        long completedCount = stepRepository.countByIncidentPlaybook_PkIncidentPlaybookIdAndIsCompletedTrue(playbookId);
        playbook.setCompletedSteps((int) completedCount);
        playbook.setIsComplete(false);
        playbookRepository.save(playbook);

        return convertToStepDTO(step);
    }

    // ==================== HELPERS ====================

    private PlaybookTemplate findTemplateOrThrow(String templateId, String tenantId) {
        return templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Playbook template not found: " + templateId));
    }

    private void recordActivity(Incident incident, String tenantId, IncidentActivityAction action,
                                 String description, String userId, String userName) {
        IncidentActivity activity = IncidentActivity.builder()
                .incident(incident)
                .tenantId(tenantId)
                .action(action)
                .description(description)
                .performedBy(userId)
                .performedByName(userName)
                .performedAt(Instant.now())
                .build();
        activityRepository.save(activity);
    }

    // ==================== CONVERTERS ====================

    private PlaybookTemplateDTO convertToTemplateDTO(PlaybookTemplate t) {
        return PlaybookTemplateDTO.builder()
                .templateId(t.getPkPlaybookTemplateId())
                .name(t.getName())
                .description(t.getDescription())
                .category(t.getCategory() != null ? t.getCategory().name() : null)
                .steps(t.getSteps())
                .isActive(t.getIsActive())
                .createdAt(formatInstant(t.getCreatedAt()))
                .updatedAt(formatInstant(t.getUpdatedAt()))
                .build();
    }

    private IncidentPlaybookDTO convertToPlaybookDTO(IncidentPlaybook pb, List<IncidentPlaybookStep> steps) {
        return IncidentPlaybookDTO.builder()
                .playbookId(pb.getPkIncidentPlaybookId())
                .templateId(pb.getPlaybookTemplate().getPkPlaybookTemplateId())
                .templateName(pb.getPlaybookTemplate().getName())
                .category(pb.getPlaybookTemplate().getCategory() != null
                        ? pb.getPlaybookTemplate().getCategory().name() : null)
                .totalSteps(pb.getTotalSteps())
                .completedSteps(pb.getCompletedSteps())
                .isComplete(pb.getIsComplete())
                .steps(steps.stream().map(this::convertToStepDTO).collect(Collectors.toList()))
                .createdAt(formatInstant(pb.getCreatedAt()))
                .build();
    }

    private IncidentPlaybookStepDTO convertToStepDTO(IncidentPlaybookStep s) {
        return IncidentPlaybookStepDTO.builder()
                .stepId(s.getPkIncidentPlaybookStepId())
                .stepNumber(s.getStepNumber())
                .title(s.getTitle())
                .description(s.getDescription())
                .isRequired(s.getIsRequired())
                .isCompleted(s.getIsCompleted())
                .completedBy(s.getCompletedBy())
                .completedByName(s.getCompletedByName())
                .completedAt(formatInstant(s.getCompletedAt()))
                .notes(s.getNotes())
                .build();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? INSTANT_FORMATTER.format(instant) : null;
    }
}
