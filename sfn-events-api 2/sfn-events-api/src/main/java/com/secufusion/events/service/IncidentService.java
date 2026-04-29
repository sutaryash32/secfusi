package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.event.IncidentNotificationEvent;
import com.secufusion.events.exception.ResourceConflictException;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentActivityRepository incidentActivityRepository;
    private final IncidentAssigneeRepository incidentAssigneeRepository;
    private final EventRepository eventRepository;
    private final TenantCacheService tenantCacheService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final DateTimeFormatter INSTANT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    // Valid status transitions
    private static final Map<IncidentStatus, Set<IncidentStatus>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(IncidentStatus.OPEN, Set.of(IncidentStatus.INVESTIGATING, IncidentStatus.RESOLVED, IncidentStatus.FALSE_POSITIVE, IncidentStatus.CLOSED, IncidentStatus.MERGED)),
            Map.entry(IncidentStatus.INVESTIGATING, Set.of(IncidentStatus.OPEN, IncidentStatus.RESOLVED, IncidentStatus.FALSE_POSITIVE, IncidentStatus.CLOSED, IncidentStatus.MERGED)),
            Map.entry(IncidentStatus.RESOLVED, Set.of(IncidentStatus.CLOSED, IncidentStatus.OPEN)),
            Map.entry(IncidentStatus.CLOSED, Set.of(IncidentStatus.OPEN)),
            Map.entry(IncidentStatus.FALSE_POSITIVE, Set.of(IncidentStatus.OPEN)),
            Map.entry(IncidentStatus.MERGED, Set.of())
    );

    // ==================== CREATE ====================

    @Transactional
    public IncidentDTO createIncident(String tenantId, String userId, String userName,
                                      CreateIncidentRequest request) {
        log.info("createIncident - tenantId={} userId={} title={}", tenantId, userId, request.getTitle());

        Tenant tenant = tenantCacheService.findByTenantId(tenantId);

        String incidentNumber = String.format("INC-%06d", incidentRepository.getNextIncidentNumber());

        Incident incident = Incident.builder()
                .tenant(tenant)
                .incidentNumber(incidentNumber)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(IncidentStatus.OPEN)
                .priority(parseIncidentPriority(request.getPriority()))
                .category(parseIncidentCategory(request.getCategory()))
                .assignedTo(request.getAssignedTo())
                .assignedToName(request.getAssignedToName())
                .assignedAt(request.getAssignedTo() != null ? Instant.now() : null)
                .source("MANUAL")
                .build();

        incident = incidentRepository.save(incident);

        // Link events if provided
        if (request.getEventIds() != null && !request.getEventIds().isEmpty()) {
            linkEventsInternal(incident, tenantId, userId, userName, request.getEventIds());
        }

        // Record activity
        recordActivity(incident, tenantId, IncidentActivityAction.CREATED,
                "Incident created: " + request.getTitle(), userId, userName, null, null);

        if (request.getAssignedTo() != null) {
            recordActivity(incident, tenantId, IncidentActivityAction.ASSIGNED,
                    "Assigned to " + request.getAssignedToName(), userId, userName,
                    null, request.getAssignedToName());
        }

        log.info("createIncident - success. incidentId={} number={}", incident.getPkIncidentId(), incidentNumber);

        // Notify: if assigned → only the assignee; otherwise → all tenant users
        if (request.getAssignedTo() != null) {
            publishNotificationEvent("INCIDENT_ASSIGNED", tenantId, userId, incident,
                    mapPriorityToSeverity(incident.getPriority()),
                    "Incident " + incidentNumber + " Assigned to You",
                    "You have been assigned to incident '" + request.getTitle() + "' by " + userName,
                    List.of(request.getAssignedTo()));
        } else {
            publishNotificationEvent("INCIDENT_CREATED", tenantId, userId, incident,
                    mapPriorityToSeverity(incident.getPriority()),
                    "Incident " + incidentNumber + " Created",
                    "A new " + incident.getPriority().name() + " incident '" + request.getTitle() + "' has been created",
                    null);
        }

        return convertToDTO(incident);
    }

    // ==================== READ ====================

    @Transactional(readOnly = true)
    public Page<IncidentDTO> getIncidents(String tenantId, String status, String priority,
                                           String category, String assignedTo, int page, int size) {
        log.debug("getIncidents - tenantId={} status={} priority={} category={} assignedTo={}",
                tenantId, status, priority, category, assignedTo);

        Pageable pageable = PageRequest.of(page, size);
        return incidentRepository.findWithFilters(tenantId, status, priority, category, assignedTo, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public IncidentDetailDTO getIncidentDetail(String tenantId, String incidentId) {
        log.debug("getIncidentDetail - tenantId={} incidentId={}", tenantId, incidentId);

        Incident incident = findIncidentOrThrow(incidentId, tenantId);

        // Fetch linked events
        List<IncidentEvent> incidentEvents = incidentEventRepository
                .findByIncident_PkIncidentIdAndTenantIdOrderByLinkedAtDesc(incidentId, tenantId);

        List<SecurityEventDTO> linkedEvents = incidentEvents.stream()
                .map(ie -> convertEventToSecurityDTO(ie.getEvent()))
                .collect(Collectors.toList());

        // Fetch recent activity (top 20)
        List<IncidentActivity> activities = incidentActivityRepository
                .findByIncident_PkIncidentIdAndTenantIdOrderByPerformedAtDesc(incidentId, tenantId);

        List<IncidentActivityDTO> recentActivity = activities.stream()
                .limit(20)
                .map(this::convertToActivityDTO)
                .collect(Collectors.toList());

        long activityCount = incidentActivityRepository
                .countByIncident_PkIncidentIdAndTenantId(incidentId, tenantId);

        return convertToDetailDTO(incident, linkedEvents, recentActivity, activityCount);
    }

    // ==================== UPDATE ====================

    @Transactional
    public IncidentDTO updateIncident(String tenantId, String userId, String userName,
                                      String incidentId, UpdateIncidentRequest request) {
        log.info("updateIncident - tenantId={} incidentId={}", tenantId, incidentId);

        Incident incident = findIncidentOrThrow(incidentId, tenantId);

        if (request.getTitle() != null) {
            String oldTitle = incident.getTitle();
            incident.setTitle(request.getTitle());
            recordActivity(incident, tenantId, IncidentActivityAction.UPDATED,
                    "Title updated", userId, userName, oldTitle, request.getTitle());
        }

        if (request.getDescription() != null) {
            incident.setDescription(request.getDescription());
        }

        if (request.getPriority() != null) {
            String oldPriority = incident.getPriority().name();
            incident.setPriority(parseIncidentPriority(request.getPriority()));
            recordActivity(incident, tenantId, IncidentActivityAction.PRIORITY_CHANGED,
                    "Priority changed from " + oldPriority + " to " + request.getPriority(),
                    userId, userName, oldPriority, request.getPriority());
        }

        if (request.getCategory() != null) {
            String oldCategory = incident.getCategory() != null ? incident.getCategory().name() : null;
            incident.setCategory(parseIncidentCategory(request.getCategory()));
            recordActivity(incident, tenantId, IncidentActivityAction.UPDATED,
                    "Category changed", userId, userName, oldCategory, request.getCategory());
        }

        incident = incidentRepository.save(incident);
        log.info("updateIncident - success. incidentId={}", incidentId);
        return convertToDTO(incident);
    }

    // ==================== STATUS CHANGE ====================

    @Transactional
    public IncidentDTO changeStatus(String tenantId, String userId, String userName,
                                    String incidentId, String newStatusStr) {
        log.info("changeStatus - tenantId={} incidentId={} newStatus={}", tenantId, incidentId, newStatusStr);

        Incident incident = findIncidentOrThrow(incidentId, tenantId);
        IncidentStatus newStatus;
        try {
            newStatus = IncidentStatus.valueOf(newStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + newStatusStr +
                    ". Valid values: " + Arrays.toString(IncidentStatus.values()));
        }
        IncidentStatus oldStatus = incident.getStatus();

        // Validate transition
        Set<IncidentStatus> allowed = VALID_TRANSITIONS.get(oldStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    "Invalid status transition: " + oldStatus + " -> " + newStatus);
        }

        incident.setStatus(newStatus);

        // Handle specific transitions
        switch (newStatus) {
            case RESOLVED -> {
                incident.setResolvedBy(userId);
                incident.setResolvedByName(userName);
                incident.setResolvedAt(Instant.now());
                markLinkedEventsAsReviewed(incidentId);
                recordActivity(incident, tenantId, IncidentActivityAction.RESOLVED,
                        "Incident resolved by " + userName, userId, userName,
                        oldStatus.name(), newStatus.name());
            }
            case CLOSED -> {
                incident.setClosedAt(Instant.now());
                if (incident.getResolvedAt() == null) {
                    // Direct close without explicit resolve
                    incident.setResolvedBy(userId);
                    incident.setResolvedByName(userName);
                    incident.setResolvedAt(Instant.now());
                    markLinkedEventsAsReviewed(incidentId);
                }
                recordActivity(incident, tenantId, IncidentActivityAction.CLOSED,
                        "Incident closed by " + userName, userId, userName,
                        oldStatus.name(), newStatus.name());
            }
            case FALSE_POSITIVE -> {
                incident.setResolvedBy(userId);
                incident.setResolvedByName(userName);
                incident.setResolvedAt(Instant.now());
                incident.setRootCause(RootCause.FALSE_POSITIVE);
                markLinkedEventsAsReviewed(incidentId);
                recordActivity(incident, tenantId, IncidentActivityAction.STATUS_CHANGED,
                        "Marked as false positive by " + userName, userId, userName,
                        oldStatus.name(), newStatus.name());
            }
            case OPEN -> {
                // Reopen — clear resolution but do NOT revert event processingStatus
                if (oldStatus == IncidentStatus.RESOLVED || oldStatus == IncidentStatus.CLOSED
                        || oldStatus == IncidentStatus.FALSE_POSITIVE) {
                    incident.setResolvedBy(null);
                    incident.setResolvedByName(null);
                    incident.setResolvedAt(null);
                    incident.setClosedAt(null);
                    incident.setResolutionNotes(null);
                    incident.setRootCause(null);
                    recordActivity(incident, tenantId, IncidentActivityAction.REOPENED,
                            "Incident reopened by " + userName, userId, userName,
                            oldStatus.name(), newStatus.name());
                } else {
                    recordActivity(incident, tenantId, IncidentActivityAction.STATUS_CHANGED,
                            "Status changed to OPEN by " + userName, userId, userName,
                            oldStatus.name(), newStatus.name());
                }
            }
            default -> {
                recordActivity(incident, tenantId, IncidentActivityAction.STATUS_CHANGED,
                        "Status changed from " + oldStatus + " to " + newStatus,
                        userId, userName, oldStatus.name(), newStatus.name());
            }
        }

        incident = incidentRepository.save(incident);
        log.info("changeStatus - success. incidentId={} {} -> {}", incidentId, oldStatus, newStatus);

        // Publish status-specific notifications (after-commit, off the DB transaction)
        switch (newStatus) {
            case RESOLVED -> publishNotificationEvent("INCIDENT_RESOLVED", tenantId, userId, incident,
                    mapPriorityToSeverity(incident.getPriority()),
                    "Incident " + incident.getIncidentNumber() + " Resolved",
                    "Incident '" + incident.getTitle() + "' has been resolved by " + userName,
                    null);
            case OPEN -> {
                if (oldStatus == IncidentStatus.RESOLVED || oldStatus == IncidentStatus.CLOSED
                        || oldStatus == IncidentStatus.FALSE_POSITIVE) {
                    publishNotificationEvent("INCIDENT_REOPENED", tenantId, userId, incident,
                            "HIGH",
                            "Incident " + incident.getIncidentNumber() + " Reopened",
                            "Incident '" + incident.getTitle() + "' has been reopened by " + userName,
                            null);
                }
            }
            default -> {
                // For non-RESOLVED/REOPENED transitions, notify assignee only
                List<String> target = incident.getAssignedTo() != null
                        ? List.of(incident.getAssignedTo()) : null;
                publishNotificationEvent("INCIDENT_STATUS_CHANGED", tenantId, userId, incident,
                        "MEDIUM",
                        "Incident " + incident.getIncidentNumber() + " Status Changed",
                        "Incident '" + incident.getTitle() + "' status changed from " + oldStatus + " to " + newStatus,
                        target);
            }
        }

        return convertToDTO(incident);
    }

    // ==================== RESOLVE ====================

    @Transactional
    public IncidentDTO resolveIncident(String tenantId, String userId, String userName,
                                       String incidentId, ResolveIncidentRequest request) {
        log.info("resolveIncident - tenantId={} incidentId={}", tenantId, incidentId);

        Incident incident = findIncidentOrThrow(incidentId, tenantId);

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedBy(userId);
        incident.setResolvedByName(userName);
        incident.setResolvedAt(Instant.now());
        incident.setResolutionNotes(request.getResolutionNotes());

        if (request.getRootCause() != null) {
            incident.setRootCause(RootCause.valueOf(request.getRootCause().toUpperCase()));
        }

        markLinkedEventsAsReviewed(incidentId);

        recordActivity(incident, tenantId, IncidentActivityAction.RESOLVED,
                "Incident resolved. Root cause: " + request.getRootCause() +
                        (request.getResolutionNotes() != null ? ". Notes: " + request.getResolutionNotes() : ""),
                userId, userName, incident.getStatus().name(), "RESOLVED");

        incident = incidentRepository.save(incident);
        log.info("resolveIncident - success. incidentId={}", incidentId);

        // Notify all tenant users about resolution (after-commit)
        publishNotificationEvent("INCIDENT_RESOLVED", tenantId, userId, incident,
                mapPriorityToSeverity(incident.getPriority()),
                "Incident " + incident.getIncidentNumber() + " Resolved",
                "Incident '" + incident.getTitle() + "' has been resolved by " + userName,
                null);

        return convertToDTO(incident);
    }

    // ==================== ASSIGN ====================

    @Transactional
    public IncidentDTO assignIncident(String tenantId, String userId, String userName,
                                      String incidentId, AssignIncidentRequest request) {
        log.info("assignIncident - tenantId={} incidentId={} assignedTo={}", tenantId, incidentId, request.getAssignedTo());

        Incident incident = findIncidentOrThrow(incidentId, tenantId);

        String oldAssignee = incident.getAssignedToName();
        incident.setAssignedTo(request.getAssignedTo());
        incident.setAssignedToName(request.getAssignedToName());
        incident.setAssignedAt(Instant.now());

        // Auto-move to INVESTIGATING if currently OPEN
        if (incident.getStatus() == IncidentStatus.OPEN) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
        }

        recordActivity(incident, tenantId, IncidentActivityAction.ASSIGNED,
                "Assigned to " + request.getAssignedToName(),
                userId, userName, oldAssignee, request.getAssignedToName());

        incident = incidentRepository.save(incident);
        log.info("assignIncident - success. incidentId={}", incidentId);

        // Notify the assignee specifically (after-commit, off the DB transaction)
        publishNotificationEvent("INCIDENT_ASSIGNED", tenantId, userId, incident,
                mapPriorityToSeverity(incident.getPriority()),
                "Incident " + incident.getIncidentNumber() + " Assigned to You",
                "You have been assigned to incident '" + incident.getTitle() + "' by " + userName,
                List.of(request.getAssignedTo()));

        return convertToDTO(incident);
    }

    // ==================== PRIORITY CHANGE ====================

    @Transactional
    public IncidentDTO changePriority(String tenantId, String userId, String userName,
                                      String incidentId, String newPriority) {
        log.info("changePriority - tenantId={} incidentId={} newPriority={}", tenantId, incidentId, newPriority);

        Incident incident = findIncidentOrThrow(incidentId, tenantId);

        String oldPriority = incident.getPriority().name();
        IncidentPriority priority;
        try {
            priority = IncidentPriority.valueOf(newPriority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: " + newPriority +
                    ". Valid values: " + Arrays.toString(IncidentPriority.values()));
        }
        incident.setPriority(priority);

        recordActivity(incident, tenantId, IncidentActivityAction.PRIORITY_CHANGED,
                "Priority changed from " + oldPriority + " to " + newPriority,
                userId, userName, oldPriority, newPriority);

        incident = incidentRepository.save(incident);
        log.info("changePriority - success. incidentId={}", incidentId);

        // Notify on escalation (new priority is higher than old) — after-commit
        if (priorityRank(priority) < priorityRank(IncidentPriority.valueOf(oldPriority))) {
            publishNotificationEvent("INCIDENT_PRIORITY_ESCALATED", tenantId, userId, incident,
                    mapPriorityToSeverity(priority),
                    "Incident " + incident.getIncidentNumber() + " Priority Escalated",
                    "Incident '" + incident.getTitle() + "' priority escalated from " + oldPriority + " to " + priority.name(),
                    null);
        }

        return convertToDTO(incident);
    }

    // ==================== EVENT LINKING ====================

    @Transactional
    public IncidentDTO linkEvents(String tenantId, String userId, String userName,
                                  String incidentId, List<String> eventIds) {
        log.info("linkEvents - tenantId={} incidentId={} eventCount={}", tenantId, incidentId, eventIds.size());

        Incident incident = findIncidentOrThrow(incidentId, tenantId);
        linkEventsInternal(incident, tenantId, userId, userName, eventIds);
        // No explicit save needed — @Transactional dirty-tracking flushes the eventCount change

        log.info("linkEvents - success. incidentId={} totalEvents={}", incidentId, incident.getEventCount());
        return convertToDTO(incident);
    }

    @Transactional
    public void unlinkEvent(String tenantId, String userId, String userName,
                            String incidentId, String eventId) {
        log.info("unlinkEvent - tenantId={} incidentId={} eventId={}", tenantId, incidentId, eventId);

        Incident incident = findIncidentOrThrow(incidentId, tenantId);

        IncidentEvent link = incidentEventRepository
                .findByIncident_PkIncidentIdAndEvent_PkEventId(incidentId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not linked to this incident"));

        incidentEventRepository.delete(link);
        incident.setEventCount(Math.max(0, incident.getEventCount() - 1));
        incidentRepository.save(incident);

        // If event is not linked to any other incident, revert to Pending
        if (!incidentEventRepository.existsByEvent_PkEventIdAndTenantId(eventId, tenantId)) {
            eventRepository.bulkUpdateProcessingStatus(List.of(eventId), "Pending");
        }

        recordActivity(incident, tenantId, IncidentActivityAction.EVENT_UNLINKED,
                "Event unlinked: " + eventId, userId, userName, eventId, null);

        log.info("unlinkEvent - success. incidentId={} eventId={}", incidentId, eventId);
    }

    @Transactional(readOnly = true)
    public List<SecurityEventDTO> getLinkedEvents(String tenantId, String incidentId) {
        log.debug("getLinkedEvents - tenantId={} incidentId={}", tenantId, incidentId);

        findIncidentOrThrow(incidentId, tenantId);

        return incidentEventRepository
                .findByIncident_PkIncidentIdAndTenantIdOrderByLinkedAtDesc(incidentId, tenantId)
                .stream()
                .map(ie -> convertEventToSecurityDTO(ie.getEvent()))
                .collect(Collectors.toList());
    }

    // ==================== TIMELINE ====================

    @Transactional(readOnly = true)
    public Page<IncidentActivityDTO> getTimeline(String tenantId, String incidentId, int page, int size) {
        log.debug("getTimeline - tenantId={} incidentId={} page={} size={}", tenantId, incidentId, page, size);

        findIncidentOrThrow(incidentId, tenantId);
        Pageable pageable = PageRequest.of(page, size);
        return incidentActivityRepository
                .findByIncident_PkIncidentIdAndTenantIdOrderByPerformedAtDesc(incidentId, tenantId, pageable)
                .map(this::convertToActivityDTO);
    }

    // ==================== COMMENTS ====================

    @Transactional
    public IncidentActivityDTO addComment(String tenantId, String userId, String userName,
                                          String incidentId, String comment) {
        log.info("addComment - tenantId={} incidentId={}", tenantId, incidentId);

        Incident incident = findIncidentOrThrow(incidentId, tenantId);

        IncidentActivity activity = recordActivity(incident, tenantId,
                IncidentActivityAction.COMMENT_ADDED, comment, userId, userName, null, null);

        log.info("addComment - success. incidentId={} activityId={}", incidentId, activity.getPkIncidentActivityId());

        // Notify assignee about the comment (after-commit)
        List<String> target = incident.getAssignedTo() != null
                ? List.of(incident.getAssignedTo()) : null;
        publishNotificationEvent("INCIDENT_COMMENT_ADDED", tenantId, userId, incident,
                "LOW",
                "Comment on Incident " + incident.getIncidentNumber(),
                userName + " commented on incident '" + incident.getTitle() + "'",
                target);

        return convertToActivityDTO(activity);
    }

    // ==================== INCIDENT MERGE ====================

    @Transactional
    public IncidentDTO mergeIncidents(String tenantId, String userId, String userName,
                                       String parentIncidentId, MergeIncidentsRequest request) {
        log.info("mergeIncidents - tenantId={} parentId={} childCount={}",
                tenantId, parentIncidentId, request.getChildIncidentIds().size());

        Incident parent = findIncidentOrThrow(parentIncidentId, tenantId);

        if (Boolean.TRUE.equals(parent.getIsMerged())) {
            throw new IllegalStateException("Cannot merge into an already-merged incident");
        }

        int totalEventsTransferred = 0;
        int mergedCount = 0;

        for (String childId : request.getChildIncidentIds()) {
            if (childId.equals(parentIncidentId)) continue;

            Incident child = findIncidentOrThrow(childId, tenantId);

            if (Boolean.TRUE.equals(child.getIsMerged())) {
                log.warn("mergeIncidents - child {} already merged, skipping", childId);
                continue;
            }

            // Transfer events from child to parent using batch queries (avoids N+1)
            List<IncidentEvent> childEvents = incidentEventRepository
                    .findByIncident_PkIncidentId(childId);

            List<String> childEventIds = childEvents.stream()
                    .map(ie -> ie.getEvent().getPkEventId())
                    .collect(Collectors.toList());

            // Single query: which of these events are already linked to the parent?
            Set<String> alreadyInParent = incidentEventRepository
                    .findByIncident_PkIncidentIdAndEvent_PkEventIdIn(parentIncidentId, childEventIds)
                    .stream()
                    .map(ie -> ie.getEvent().getPkEventId())
                    .collect(Collectors.toSet());

            // parent is reassigned after the loop so capture it as an effectively-final ref
            final Incident parentRef = parent;
            List<IncidentEvent> newLinks = childEvents.stream()
                    .filter(ie -> !alreadyInParent.contains(ie.getEvent().getPkEventId()))
                    .map(ie -> IncidentEvent.builder()
                            .incident(parentRef)
                            .event(ie.getEvent())
                            .tenantId(tenantId)
                            .linkedAt(Instant.now())
                            .linkedBy("merge:" + userName)
                            .build())
                    .collect(Collectors.toList());

            if (!newLinks.isEmpty()) {
                incidentEventRepository.saveAll(newLinks);
            }
            int transferred = newLinks.size();
            totalEventsTransferred += transferred;

            // Close child and mark as merged
            child.setStatus(IncidentStatus.MERGED);
            child.setMergedInto(parent);
            child.setIsMerged(true);
            child.setClosedAt(Instant.now());
            incidentRepository.save(child);

            // Record activity on child
            recordActivity(child, tenantId, IncidentActivityAction.MERGED,
                    "Merged into " + parent.getIncidentNumber()
                            + (request.getMergeNotes() != null ? ". Notes: " + request.getMergeNotes() : ""),
                    userId, userName, null, parent.getIncidentNumber());

            // Record activity on parent
            recordActivity(parent, tenantId, IncidentActivityAction.MERGE_RECEIVED,
                    "Received merge from " + child.getIncidentNumber()
                            + " (" + transferred + " events transferred)",
                    userId, userName, child.getIncidentNumber(), null);

            // Escalate parent priority if child had higher priority
            if (priorityRank(child.getPriority()) < priorityRank(parent.getPriority())) {
                IncidentPriority oldPriority = parent.getPriority();
                parent.setPriority(child.getPriority());
                recordActivity(parent, tenantId, IncidentActivityAction.PRIORITY_CHANGED,
                        "Priority escalated due to merge from " + child.getIncidentNumber(),
                        userId, userName, oldPriority.name(), child.getPriority().name());
            }

            mergedCount++;
        }

        parent.setEventCount(parent.getEventCount() + totalEventsTransferred);
        parent = incidentRepository.save(parent);

        log.info("mergeIncidents - done. parentId={} merged={} eventsTransferred={}",
                parentIncidentId, mergedCount, totalEventsTransferred);

        publishNotificationEvent("INCIDENT_MERGED", tenantId, userId, parent,
                mapPriorityToSeverity(parent.getPriority()),
                mergedCount + " Incident(s) Merged into " + parent.getIncidentNumber(),
                mergedCount + " incident(s) merged into " + parent.getIncidentNumber()
                        + " (" + totalEventsTransferred + " events transferred)",
                null);

        return convertToDTO(parent);
    }

    // ==================== BULK OPERATIONS ====================

    @Transactional
    public BulkOperationResult bulkAssign(String tenantId, String userId, String userName,
                                           BulkAssignRequest request) {
        log.info("bulkAssign - tenantId={} count={} assignedTo={}", tenantId,
                request.getIncidentIds().size(), request.getAssignedTo());

        int succeeded = 0;
        List<BulkOperationResult.BulkError> errors = new ArrayList<>();
        AssignIncidentRequest assignReq = new AssignIncidentRequest(
                request.getAssignedTo(), request.getAssignedToName());

        for (String incidentId : request.getIncidentIds()) {
            try {
                assignIncident(tenantId, userId, userName, incidentId, assignReq);
                succeeded++;
            } catch (Exception e) {
                errors.add(BulkOperationResult.BulkError.builder()
                        .incidentId(incidentId).reason(e.getMessage()).build());
            }
        }

        log.info("bulkAssign - done. succeeded={} failed={}", succeeded, errors.size());
        return BulkOperationResult.builder()
                .total(request.getIncidentIds().size())
                .succeeded(succeeded).failed(errors.size()).errors(errors).build();
    }

    @Transactional
    public BulkOperationResult bulkStatusChange(String tenantId, String userId, String userName,
                                                  BulkStatusRequest request) {
        log.info("bulkStatusChange - tenantId={} count={} newStatus={}", tenantId,
                request.getIncidentIds().size(), request.getNewStatus());

        int succeeded = 0;
        List<BulkOperationResult.BulkError> errors = new ArrayList<>();

        for (String incidentId : request.getIncidentIds()) {
            try {
                changeStatus(tenantId, userId, userName, incidentId, request.getNewStatus());
                succeeded++;
            } catch (Exception e) {
                errors.add(BulkOperationResult.BulkError.builder()
                        .incidentId(incidentId).reason(e.getMessage()).build());
            }
        }

        log.info("bulkStatusChange - done. succeeded={} failed={}", succeeded, errors.size());
        return BulkOperationResult.builder()
                .total(request.getIncidentIds().size())
                .succeeded(succeeded).failed(errors.size()).errors(errors).build();
    }

    @Transactional
    public BulkOperationResult bulkClose(String tenantId, String userId, String userName,
                                          BulkCloseRequest request) {
        log.info("bulkClose - tenantId={} count={}", tenantId, request.getIncidentIds().size());

        int succeeded = 0;
        List<BulkOperationResult.BulkError> errors = new ArrayList<>();

        for (String incidentId : request.getIncidentIds()) {
            try {
                Incident incident = findIncidentOrThrow(incidentId, tenantId);
                IncidentStatus oldStatus = incident.getStatus();

                incident.setStatus(IncidentStatus.CLOSED);
                incident.setClosedAt(Instant.now());
                if (request.getResolutionNotes() != null) {
                    incident.setResolutionNotes(request.getResolutionNotes());
                }
                incidentRepository.save(incident);

                recordActivity(incident, tenantId, IncidentActivityAction.CLOSED,
                        "Bulk closed. " + (request.getResolutionNotes() != null
                                ? "Notes: " + request.getResolutionNotes() : ""),
                        userId, userName, oldStatus.name(), "CLOSED");

                succeeded++;
            } catch (Exception e) {
                errors.add(BulkOperationResult.BulkError.builder()
                        .incidentId(incidentId).reason(e.getMessage()).build());
            }
        }

        log.info("bulkClose - done. succeeded={} failed={}", succeeded, errors.size());
        return BulkOperationResult.builder()
                .total(request.getIncidentIds().size())
                .succeeded(succeeded).failed(errors.size()).errors(errors).build();
    }

    // ==================== STATS & DASHBOARD ====================

    @Transactional(readOnly = true)
    public IncidentStatsDTO getStats(String tenantId) {
        log.debug("getStats - tenantId={}", tenantId);

        long total = incidentRepository.countByTenant_TenantID(tenantId);
        long open = incidentRepository.countByTenant_TenantIDAndStatus(tenantId, IncidentStatus.OPEN);
        long investigating = incidentRepository.countByTenant_TenantIDAndStatus(tenantId, IncidentStatus.INVESTIGATING);
        long resolved = incidentRepository.countByTenant_TenantIDAndStatus(tenantId, IncidentStatus.RESOLVED);
        long closed = incidentRepository.countByTenant_TenantIDAndStatus(tenantId, IncidentStatus.CLOSED);
        long falsePositive = incidentRepository.countByTenant_TenantIDAndStatus(tenantId, IncidentStatus.FALSE_POSITIVE);

        Map<String, Long> byPriority = new HashMap<>();
        incidentRepository.countOpenByPriority(tenantId).forEach(row -> {
            String priority = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            byPriority.put(priority, count);
        });

        Map<String, Long> byCategory = new HashMap<>();
        incidentRepository.countByCategory(tenantId).forEach(row -> {
            String category = row[0] != null ? row[0].toString() : "unknown";
            Long count = ((Number) row[1]).longValue();
            byCategory.put(category, count);
        });

        Double mttr = incidentRepository.calculateMTTR(tenantId);

        return IncidentStatsDTO.builder()
                .totalIncidents(total)
                .openCount(open)
                .investigatingCount(investigating)
                .resolvedCount(resolved)
                .closedCount(closed)
                .falsePositiveCount(falsePositive)
                .byPriority(byPriority)
                .byCategory(byCategory)
                .meanTimeToResolveHours(mttr)
                .build();
    }

    @Transactional(readOnly = true)
    public IncidentDashboardDTO getDashboard(String tenantId) {
        log.debug("getDashboard - tenantId={}", tenantId);

        IncidentStatsDTO stats = getStats(tenantId);
        List<IncidentDTO> recentIncidents = incidentRepository
                .findTop10ByTenant_TenantIDOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return IncidentDashboardDTO.builder()
                .stats(stats)
                .recentIncidents(recentIncidents)
                .generatedAt(java.time.LocalDateTime.now())
                .build();
    }

    // ==================== PENDING EVENT MANAGEMENT ====================

    /**
     * Get counts of pending security events eligible for incident creation.
     * Helps frontend understand the scope before running auto-create or bulk-dismiss.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getPendingEventCounts(String tenantId) {
        log.debug("getPendingEventCounts - tenantId={}", tenantId);

        long totalPending = eventRepository.countPendingSecurityEvents(tenantId);
        long criticalHighPending = eventRepository.countPendingHighSeverityEvents(tenantId);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("totalPendingSecurityEvents", totalPending);
        counts.put("criticalHighPending", criticalHighPending);
        counts.put("mediumLowPending", totalPending - criticalHighPending);
        return counts;
    }

    /**
     * Bulk dismiss old pending events before a cutoff date.
     * Sets them to 'Reviewed' so they won't trigger incidents.
     * Use this after first deployment to skip historical events.
     */
    @Transactional
    public int bulkDismissOldEvents(String tenantId, String userId, String userName,
                                     java.time.LocalDateTime cutoffDate) {
        log.info("bulkDismissOldEvents - tenantId={} cutoffDate={}", tenantId, cutoffDate);

        int dismissed = eventRepository.bulkDismissOldPendingEvents(tenantId, cutoffDate);
        log.info("bulkDismissOldEvents - success. tenantId={} eventsDismissed={}", tenantId, dismissed);
        return dismissed;
    }

    // ==================== AUTO-CREATION ====================

    @Transactional
    public List<IncidentDTO> autoCreateIncidents(String tenantId, String userId, String userName) {
        log.info("autoCreateIncidents - tenantId={}", tenantId);

        Tenant tenant = tenantCacheService.findByTenantId(tenantId);

        List<Event> pendingEvents = eventRepository.findPendingHighSeverityEvents(tenantId);
        if (pendingEvents.isEmpty()) {
            log.info("autoCreateIncidents - no pending high-severity events found. tenantId={}", tenantId);
            return Collections.emptyList();
        }

        // Group events by threat type
        Map<String, List<Event>> groupedByThreat = pendingEvents.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getThreatType() != null ? e.getThreatType().toLowerCase() : "unknown"));

        List<IncidentDTO> createdIncidents = new ArrayList<>();

        for (Map.Entry<String, List<Event>> entry : groupedByThreat.entrySet()) {
            String threatType = entry.getKey();
            List<Event> events = entry.getValue();
            IncidentCategory category = mapThreatTypeToCategory(threatType);

            // Check if open incident already exists for this category
            List<Incident> existingOpen = incidentRepository.findOpenByCategory(tenantId, category);

            Incident incident;
            boolean isNew = existingOpen.isEmpty();

            if (!isNew) {
                // Link to existing incident
                incident = existingOpen.get(0);
                log.info("autoCreateIncidents - linking {} events to existing incident {}",
                        events.size(), incident.getIncidentNumber());
            } else {
                // Create new incident
                boolean hasCritical = events.stream()
                        .anyMatch(e -> "critical".equalsIgnoreCase(e.getSeverity()));
                IncidentPriority priority = hasCritical ? IncidentPriority.P1_CRITICAL : IncidentPriority.P2_HIGH;

                String incidentNumber = String.format("INC-%06d", incidentRepository.getNextIncidentNumber());
                String title = "Auto: " + formatCategoryName(category) + " detected - " + events.size() + " events";

                incident = Incident.builder()
                        .tenant(tenant)
                        .incidentNumber(incidentNumber)
                        .title(title)
                        .description("Auto-created from " + events.size() + " " + threatType + " security events")
                        .status(IncidentStatus.OPEN)
                        .priority(priority)
                        .category(category)
                        .source("AUTO")
                        .autoRuleName("severity-based-auto-create")
                        .build();

                incident = incidentRepository.save(incident);

                recordActivity(incident, tenantId, IncidentActivityAction.CREATED,
                        "Auto-created from " + events.size() + " " + threatType + " events (severity: " +
                                (hasCritical ? "critical" : "high") + ")",
                        userId, userName, null, null);

                // Auto-assign based on configured assignees (least-loaded)
                tryAutoAssign(incident, tenantId, userId, userName);

                log.info("autoCreateIncidents - created incident {} for {} events (assignedTo={})",
                        incident.getIncidentNumber(), events.size(), incident.getAssignedToName());
            }

            // Notifications — after the if/else so `incident` is fully resolved
            if (isNew) {
                boolean hasCritical = events.stream()
                        .anyMatch(e -> "critical".equalsIgnoreCase(e.getSeverity()));

                publishAutoIncidentNotification("INCIDENT_CREATED", tenantId, userId, incident,
                        hasCritical ? "CRITICAL" : "HIGH",
                        "Auto-Incident " + incident.getIncidentNumber() + " Created",
                        "Auto-created incident from " + events.size() + " " + threatType + " security events",
                        events);

                // If auto-assigned, also notify the assignee
                if (incident.getAssignedTo() != null) {
                    publishAutoIncidentNotification("INCIDENT_ASSIGNED", tenantId, userId, incident,
                            hasCritical ? "CRITICAL" : "HIGH",
                            "Incident " + incident.getIncidentNumber() + " Assigned to You",
                            "You have been auto-assigned to incident '" + incident.getTitle() + "' with "
                                    + events.size() + " " + threatType + " events",
                            events);
                }
            } else {
                // Existing incident — notify about new events linked
                publishAutoIncidentNotification("INCIDENT_EVENTS_LINKED", tenantId, userId, incident,
                        "MEDIUM",
                        events.size() + " New Events Linked to " + incident.getIncidentNumber(),
                        events.size() + " new " + threatType + " security events linked to existing incident '"
                                + incident.getTitle() + "'",
                        events);
            }

            // Link events to incident
            // No explicit save after linkEventsInternal — @Transactional dirty-tracking flushes eventCount
            List<String> eventIds = events.stream()
                    .map(Event::getPkEventId)
                    .collect(Collectors.toList());
            linkEventsInternal(incident, tenantId, userId, userName, eventIds);

            createdIncidents.add(convertToDTO(incident));
        }

        log.info("autoCreateIncidents - completed. tenantId={} incidentsCreated/Updated={}",
                tenantId, createdIncidents.size());
        return createdIncidents;
    }

    // ==================== PRIVATE HELPERS ====================

    private Incident findIncidentOrThrow(String incidentId, String tenantId) {
        return incidentRepository.findByPkIncidentIdAndTenant_TenantID(incidentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
    }

    private void linkEventsInternal(Incident incident, String tenantId, String userId,
                                    String userName, List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }

        // BATCH FETCH 1: validate all events belong to this tenant in one query (was N selects)
        Map<String, Event> validEventMap = eventRepository
                .findAllByPkEventIdInAndTenant_TenantID(eventIds, tenantId)
                .stream()
                .collect(Collectors.toMap(Event::getPkEventId, e -> e));

        // Warn about any event IDs that were not found / wrong tenant
        eventIds.stream()
                .filter(id -> !validEventMap.containsKey(id))
                .forEach(id -> log.warn("linkEventsInternal - event not found or wrong tenant. eventId={}", id));

        if (validEventMap.isEmpty()) {
            return;
        }

        // BATCH FETCH 2: find all existing links in one query (was N selects)
        Set<String> alreadyLinked = incidentEventRepository
                .findByIncident_PkIncidentIdAndEvent_PkEventIdIn(
                        incident.getPkIncidentId(), new ArrayList<>(validEventMap.keySet()))
                .stream()
                .map(ie -> ie.getEvent().getPkEventId())
                .collect(Collectors.toSet());

        alreadyLinked.forEach(id ->
                log.debug("linkEventsInternal - event already linked. eventId={}", id));

        // Build new links in memory for the events not already linked
        List<IncidentEvent> newLinks = validEventMap.entrySet().stream()
                .filter(e -> !alreadyLinked.contains(e.getKey()))
                .map(e -> IncidentEvent.builder()
                        .incident(incident)
                        .event(e.getValue())
                        .tenantId(tenantId)
                        .linkedAt(Instant.now())
                        .linkedBy(userName)
                        .build())
                .collect(Collectors.toList());

        if (newLinks.isEmpty()) {
            return;
        }

        // BATCH INSERT: one saveAll instead of N individual saves
        incidentEventRepository.saveAll(newLinks);

        List<String> linkedEventIds = newLinks.stream()
                .map(ie -> ie.getEvent().getPkEventId())
                .collect(Collectors.toList());

        // Bulk update event processing status to "Processed"
        eventRepository.bulkUpdateProcessingStatus(linkedEventIds, "Processed");
        incident.setEventCount(incident.getEventCount() + linkedEventIds.size());

        recordActivity(incident, tenantId, IncidentActivityAction.EVENT_LINKED,
                linkedEventIds.size() + " event(s) linked", userId, userName,
                null, String.valueOf(linkedEventIds.size()));
    }

    private void markLinkedEventsAsReviewed(String incidentId) {
        List<IncidentEvent> links = incidentEventRepository.findByIncident_PkIncidentId(incidentId);
        if (links.isEmpty()) return;

        List<String> eventIds = links.stream()
                .map(ie -> ie.getEvent().getPkEventId())
                .collect(Collectors.toList());

        int updated = eventRepository.bulkUpdateProcessingStatus(eventIds, "Reviewed");
        log.debug("markLinkedEventsAsReviewed - incidentId={} eventsUpdated={}", incidentId, updated);
    }

    private IncidentActivity recordActivity(Incident incident, String tenantId,
                                            IncidentActivityAction action, String description,
                                            String userId, String userName,
                                            String oldValue, String newValue) {
        IncidentActivity activity = IncidentActivity.builder()
                .incident(incident)
                .tenantId(tenantId)
                .action(action)
                .description(description)
                .performedBy(userId)
                .performedByName(userName)
                .performedAt(Instant.now())
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        return incidentActivityRepository.save(activity);
    }

    // ==================== CONVERTERS ====================

    private IncidentDTO convertToDTO(Incident incident) {
        // Resolve mergedInto once to avoid triggering two lazy-load proxy initialisations
        Incident mergedInto = incident.getMergedInto();
        return IncidentDTO.builder()
                .incidentId(incident.getPkIncidentId())
                .incidentNumber(incident.getIncidentNumber())
                .title(incident.getTitle())
                .status(incident.getStatus().name())
                .priority(incident.getPriority().name())
                .category(incident.getCategory() != null ? incident.getCategory().name() : null)
                .assignedTo(incident.getAssignedTo())
                .assignedToName(incident.getAssignedToName())
                .eventCount(incident.getEventCount())
                .source(incident.getSource())
                .createdAt(formatInstant(incident.getCreatedAt()))
                .updatedAt(formatInstant(incident.getUpdatedAt()))
                .resolvedAt(formatInstant(incident.getResolvedAt()))
                .closedAt(formatInstant(incident.getClosedAt()))
                .mergedIntoId(mergedInto != null ? mergedInto.getPkIncidentId() : null)
                .mergedIntoNumber(mergedInto != null ? mergedInto.getIncidentNumber() : null)
                .isMerged(incident.getIsMerged())
                .build();
    }

    private IncidentDetailDTO convertToDetailDTO(Incident incident, List<SecurityEventDTO> linkedEvents,
                                                  List<IncidentActivityDTO> recentActivity, long activityCount) {
        return IncidentDetailDTO.builder()
                .incidentId(incident.getPkIncidentId())
                .incidentNumber(incident.getIncidentNumber())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .status(incident.getStatus().name())
                .priority(incident.getPriority().name())
                .category(incident.getCategory() != null ? incident.getCategory().name() : null)
                .assignedTo(incident.getAssignedTo())
                .assignedToName(incident.getAssignedToName())
                .assignedAt(formatInstant(incident.getAssignedAt()))
                .resolvedBy(incident.getResolvedBy())
                .resolvedByName(incident.getResolvedByName())
                .resolvedAt(formatInstant(incident.getResolvedAt()))
                .resolutionNotes(incident.getResolutionNotes())
                .rootCause(incident.getRootCause() != null ? incident.getRootCause().name() : null)
                .closedAt(formatInstant(incident.getClosedAt()))
                .source(incident.getSource())
                .autoRuleName(incident.getAutoRuleName())
                .createdBy(incident.getCreatedBy())
                .updatedBy(incident.getUpdatedBy())
                .createdAt(formatInstant(incident.getCreatedAt()))
                .updatedAt(formatInstant(incident.getUpdatedAt()))
                .linkedEvents(linkedEvents)
                .eventCount(incident.getEventCount())
                .recentActivity(recentActivity)
                .activityCount(activityCount)
                .build();
    }

    private IncidentActivityDTO convertToActivityDTO(IncidentActivity activity) {
        return IncidentActivityDTO.builder()
                .activityId(activity.getPkIncidentActivityId())
                .action(activity.getAction().name())
                .description(activity.getDescription())
                .performedBy(activity.getPerformedBy())
                .performedByName(activity.getPerformedByName())
                .performedAt(formatInstant(activity.getPerformedAt()))
                .oldValue(activity.getOldValue())
                .newValue(activity.getNewValue())
                .build();
    }

    private SecurityEventDTO convertEventToSecurityDTO(Event event) {
        SecurityEventDTO.SecurityEventDTOBuilder builder = SecurityEventDTO.builder()
                .eventId(event.getPkEventId())
                .url(event.getUrl())
                .timeStamp(event.getTimeStamp() != null ? event.getTimeStamp().toString() : null)
                .userName(event.getUserName())
                .deviceId(event.getDevice() != null ? event.getDevice().getDeviceId() : null)
                .eventType(event.getEventType() != null ? event.getEventType().name() : null)
                .severity(event.getSeverity())
                .threatType(event.getThreatType())
                .threatLevel(event.getThreatLevel())
                .actionTaken(event.getActionTaken())
                .riskLevel(event.getRiskLevel())
                .policyName(event.getPolicyName())
                .policyType(event.getPolicyType())
                .domain(event.getDomain())
                .title(event.getTitle())
                .ipAddress(event.getIpAddress())
                .complianceImpact(event.getComplianceImpact())
                .processingStatus(event.getProcessingStatus());

        if (event.getDevice() != null) {
            builder.deviceName(event.getDevice().getDeviceName())
                    .osInfo(event.getDevice().getOsInfo())
                    .deviceStatus(event.getDevice().getStatus() != null ?
                            event.getDevice().getStatus().name() : null);
        }

        return builder.build();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? INSTANT_FORMATTER.format(instant) : null;
    }

    private IncidentPriority parseIncidentPriority(String priority) {
        if (priority == null) return IncidentPriority.P3_MEDIUM;
        try {
            return IncidentPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IncidentPriority.P3_MEDIUM;
        }
    }

    private IncidentCategory parseIncidentCategory(String category) {
        if (category == null) return null;
        try {
            return IncidentCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IncidentCategory.OTHER;
        }
    }

    private IncidentCategory mapThreatTypeToCategory(String threatType) {
        if (threatType == null) return IncidentCategory.OTHER;
        return switch (threatType.toLowerCase()) {
            case "phishing" -> IncidentCategory.PHISHING;
            case "malware" -> IncidentCategory.MALWARE;
            case "dlp", "data_leak" -> IncidentCategory.DATA_LEAK;
            case "csp_violation" -> IncidentCategory.CSP_VIOLATION;
            case "xss" -> IncidentCategory.XSS_ATTACK;
            default -> {
                if (threatType.contains("policy")) yield IncidentCategory.POLICY_VIOLATION;
                else if (threatType.contains("compliance")) yield IncidentCategory.COMPLIANCE_BREACH;
                else yield IncidentCategory.OTHER;
            }
        };
    }

    // ==================== NOTIFICATION HELPERS ====================

    /**
     * Publishes a Spring Application Event that is picked up by {@code IncidentNotificationListener}
     * AFTER the current DB transaction commits (TransactionPhase.AFTER_COMMIT + @Async).
     *
     * Benefits:
     *  - DB connection is released before any Kafka I/O begins.
     *  - Kafka is never called if the transaction rolls back.
     *  - Incident data is captured eagerly (still inside the transaction) so the
     *    listener does not need to re-query the database.
     */
    private void publishNotificationEvent(String type, String tenantId, String actorUserId,
                                           Incident incident, String severity, String title,
                                           String message, List<String> targetUserIds) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("incidentId", incident.getPkIncidentId());
        metadata.put("incidentNumber", incident.getIncidentNumber());
        if (incident.getPriority() != null) {
            metadata.put("priority", incident.getPriority().name());
        }
        if (incident.getCategory() != null) {
            metadata.put("category", incident.getCategory().name());
        }

        applicationEventPublisher.publishEvent(new IncidentNotificationEvent(
                type, tenantId, actorUserId,
                incident.getPkIncidentId(), incident.getIncidentNumber(),
                incident.getPriority() != null ? incident.getPriority().name() : null,
                incident.getCategory() != null ? incident.getCategory().name() : null,
                severity, title, message, targetUserIds, metadata));
    }

    /**
     * Publishes a notification for auto-created/linked incidents, including security event details in metadata.
     */
    private void publishAutoIncidentNotification(String type, String tenantId, String actorUserId,
                                                  Incident incident, String severity, String title,
                                                  String message, List<Event> events) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("incidentId", incident.getPkIncidentId());
        metadata.put("incidentNumber", incident.getIncidentNumber());
        if (incident.getPriority() != null) {
            metadata.put("priority", incident.getPriority().name());
        }
        if (incident.getCategory() != null) {
            metadata.put("category", incident.getCategory().name());
        }

        // Include linked event summary
        metadata.put("eventCount", String.valueOf(events.size()));
        if (!events.isEmpty()) {
            Event first = events.get(0);
            metadata.put("threatType", first.getThreatType() != null ? first.getThreatType() : "unknown");
            metadata.put("topSeverity", events.stream()
                    .map(Event::getSeverity)
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingInt(this::eventSeverityRank))
                    .orElse("unknown"));

            // Include up to 5 event IDs for reference
            String eventIds = events.stream()
                    .map(Event::getPkEventId)
                    .limit(5)
                    .collect(Collectors.joining(","));
            metadata.put("linkedEventIds", eventIds);
        }

        // For INCIDENT_ASSIGNED, target only the assignee
        List<String> targetUserIds = "INCIDENT_ASSIGNED".equals(type) && incident.getAssignedTo() != null
                ? List.of(incident.getAssignedTo())
                : null;

        applicationEventPublisher.publishEvent(new IncidentNotificationEvent(
                type, tenantId, actorUserId,
                incident.getPkIncidentId(), incident.getIncidentNumber(),
                incident.getPriority() != null ? incident.getPriority().name() : null,
                incident.getCategory() != null ? incident.getCategory().name() : null,
                severity, title, message, targetUserIds, metadata));
    }

    private int eventSeverityRank(String severity) {
        return switch (severity != null ? severity.toLowerCase() : "info") {
            case "critical" -> 1;
            case "high" -> 2;
            case "medium" -> 3;
            case "low" -> 4;
            default -> 5;
        };
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

    private int priorityRank(IncidentPriority priority) {
        if (priority == null) return 5;
        return switch (priority) {
            case P1_CRITICAL -> 1;
            case P2_HIGH -> 2;
            case P3_MEDIUM -> 3;
            case P4_LOW -> 4;
        };
    }

    private String formatCategoryName(IncidentCategory category) {
        if (category == null) return "Unknown";
        return switch (category) {
            case MALWARE -> "Malware";
            case PHISHING -> "Phishing";
            case DATA_LEAK -> "Data Leak";
            case POLICY_VIOLATION -> "Policy Violation";
            case UNAUTHORIZED_ACCESS -> "Unauthorized Access";
            case CSP_VIOLATION -> "CSP Violation";
            case XSS_ATTACK -> "XSS Attack";
            case INSIDER_THREAT -> "Insider Threat";
            case COMPLIANCE_BREACH -> "Compliance Breach";
            case OTHER -> "Other";
        };
    }

    // ==================== AUTO-ASSIGNMENT ====================

    /**
     * Attempt to auto-assign an incident based on configured assignees.
     * Uses least-loaded algorithm: picks the assignee with fewest OPEN+INVESTIGATING incidents.
     * Tiebreak: lowest assignment_order wins.
     */
    private boolean tryAutoAssign(Incident incident, String tenantId,
                                   String userId, String userName) {
        IncidentCategory category = incident.getCategory();

        // Step 1: Find category-specific active assignees
        List<IncidentAssignee> candidates = (category != null)
                ? incidentAssigneeRepository
                    .findByTenant_TenantIDAndCategoryAndIsActiveTrueOrderByAssignmentOrderAsc(
                        tenantId, category)
                : Collections.emptyList();

        // Step 2: Fallback to "all categories" assignees if none found
        if (candidates.isEmpty()) {
            candidates = incidentAssigneeRepository
                    .findByTenant_TenantIDAndCategoryIsNullAndIsActiveTrueOrderByAssignmentOrderAsc(
                        tenantId);
        }

        // Step 3: If still no candidates, auto-seed a catch-all config for the requesting user
        if (candidates.isEmpty()) {
            log.info("tryAutoAssign - no assignees configured, auto-seeding catch-all for userId={} tenantId={}",
                    userId, tenantId);
            IncidentAssignee seeded = autoSeedDefaultAssignee(tenantId, userId, userName);
            if (seeded != null) {
                candidates = List.of(seeded);
            } else {
                return false;
            }
        }

        // Step 4: Find least-loaded candidate (already ordered by assignmentOrder ASC for tiebreak)
        IncidentAssignee bestCandidate = null;
        long lowestLoad = Long.MAX_VALUE;

        for (IncidentAssignee candidate : candidates) {
            long load = incidentRepository.countActiveIncidentsByAssignee(
                    tenantId, candidate.getUserId());
            if (load < lowestLoad) {
                lowestLoad = load;
                bestCandidate = candidate;
            }
        }

        if (bestCandidate == null) {
            return false;
        }

        // Step 5: Apply assignment
        incident.setAssignedTo(bestCandidate.getUserId());
        incident.setAssignedToName(bestCandidate.getUserName());
        incident.setAssignedAt(Instant.now());

        // Step 6: Auto-transition OPEN -> INVESTIGATING
        if (incident.getStatus() == IncidentStatus.OPEN) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
        }

        // Step 7: Record activity
        recordActivity(incident, tenantId, IncidentActivityAction.ASSIGNED,
                "Auto-assigned to " + bestCandidate.getUserName() +
                        " (load: " + lowestLoad + " active incidents)",
                userId, userName, null, bestCandidate.getUserName());

        log.info("tryAutoAssign - assigned incident {} to {} (load={})",
                incident.getIncidentNumber(), bestCandidate.getUserName(), lowestLoad);
        return true;
    }

    /**
     * Auto-seed a catch-all assignee config for the requesting user.
     * Called when tryAutoAssign() finds no configured assignees.
     * Returns the newly created assignee, or null if it already exists.
     */
    private IncidentAssignee autoSeedDefaultAssignee(String tenantId, String userId, String userName) {
        // Check if this user already has a catch-all config (shouldn't happen, but safety check)
        if (incidentAssigneeRepository.existsByTenant_TenantIDAndCategoryIsNullAndUserId(tenantId, userId)) {
            log.debug("autoSeedDefaultAssignee - catch-all already exists for userId={}", userId);
            return null;
        }

        Tenant tenant;
        try {
            tenant = tenantCacheService.findByTenantId(tenantId);
        } catch (ResourceNotFoundException e) {
            return null;
        }

        IncidentAssignee assignee = IncidentAssignee.builder()
                .tenant(tenant)
                .category(null)  // null = catch-all for ALL categories
                .userId(userId)
                .userName(userName)
                .isActive(true)
                .assignmentOrder(0)
                .build();

        assignee = incidentAssigneeRepository.save(assignee);
        log.info("autoSeedDefaultAssignee - created catch-all assignee config for {} ({})",
                userName, userId);
        return assignee;
    }

    // ==================== ASSIGNEE CONFIGURATION CRUD ====================

    @Transactional
    public IncidentAssigneeDTO addAssigneeConfig(String tenantId, String userId, String userName,
                                                  CreateIncidentAssigneeRequest request) {
        log.info("addAssigneeConfig - tenantId={} category={} assigneeUserId={}",
                tenantId, request.getCategory(), request.getUserId());

        Tenant tenant = tenantCacheService.findByTenantId(tenantId);

        IncidentCategory category = parseIncidentCategory(request.getCategory());

        // Check for duplicate
        boolean exists = (category != null)
                ? incidentAssigneeRepository.existsByTenant_TenantIDAndCategoryAndUserId(
                    tenantId, category, request.getUserId())
                : incidentAssigneeRepository.existsByTenant_TenantIDAndCategoryIsNullAndUserId(
                    tenantId, request.getUserId());

        if (exists) {
            throw new ResourceConflictException(
                    "Assignee config already exists for this user and category");
        }

        IncidentAssignee assignee = IncidentAssignee.builder()
                .tenant(tenant)
                .category(category)
                .userId(request.getUserId())
                .userName(request.getUserName())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .assignmentOrder(request.getAssignmentOrder() != null ? request.getAssignmentOrder() : 0)
                .build();

        assignee = incidentAssigneeRepository.save(assignee);
        log.info("addAssigneeConfig - success. id={}", assignee.getPkIncidentAssigneeId());
        return convertToAssigneeDTO(assignee, tenantId);
    }

    @Transactional(readOnly = true)
    public List<IncidentAssigneeDTO> listAssigneeConfigs(String tenantId) {
        log.debug("listAssigneeConfigs - tenantId={}", tenantId);

        return incidentAssigneeRepository
                .findByTenant_TenantIDOrderByAssignmentOrderAsc(tenantId)
                .stream()
                .map(a -> convertToAssigneeDTO(a, tenantId))
                .collect(Collectors.toList());
    }

    @Transactional
    public IncidentAssigneeDTO updateAssigneeConfig(String tenantId, String assigneeId,
                                                      Boolean isActive, Integer assignmentOrder,
                                                      String newUserName) {
        log.info("updateAssigneeConfig - tenantId={} assigneeId={}", tenantId, assigneeId);

        IncidentAssignee assignee = incidentAssigneeRepository
                .findByPkIncidentAssigneeIdAndTenant_TenantID(assigneeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignee config not found: " + assigneeId));

        if (isActive != null) assignee.setIsActive(isActive);
        if (assignmentOrder != null) assignee.setAssignmentOrder(assignmentOrder);
        if (newUserName != null) assignee.setUserName(newUserName);

        assignee = incidentAssigneeRepository.save(assignee);
        log.info("updateAssigneeConfig - success. id={}", assigneeId);
        return convertToAssigneeDTO(assignee, tenantId);
    }

    @Transactional
    public void removeAssigneeConfig(String tenantId, String assigneeId) {
        log.info("removeAssigneeConfig - tenantId={} assigneeId={}", tenantId, assigneeId);

        IncidentAssignee assignee = incidentAssigneeRepository
                .findByPkIncidentAssigneeIdAndTenant_TenantID(assigneeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignee config not found: " + assigneeId));

        incidentAssigneeRepository.delete(assignee);
        log.info("removeAssigneeConfig - success. id={}", assigneeId);
    }

    /**
     * Bulk initialize assignee configs from a list of user entries.
     * Skips duplicates (same user + category already configured).
     * Returns list of created configs.
     */
    @Transactional
    public List<IncidentAssigneeDTO> bulkInitAssigneeConfigs(String tenantId,
                                                               List<CreateIncidentAssigneeRequest> requests) {
        log.info("bulkInitAssigneeConfigs - tenantId={} count={}", tenantId, requests.size());

        Tenant tenant = tenantCacheService.findByTenantId(tenantId);

        List<IncidentAssigneeDTO> created = new ArrayList<>();
        int skipped = 0;

        for (CreateIncidentAssigneeRequest req : requests) {
            IncidentCategory category = parseIncidentCategory(req.getCategory());

            // Skip if duplicate
            boolean exists = (category != null)
                    ? incidentAssigneeRepository.existsByTenant_TenantIDAndCategoryAndUserId(
                        tenantId, category, req.getUserId())
                    : incidentAssigneeRepository.existsByTenant_TenantIDAndCategoryIsNullAndUserId(
                        tenantId, req.getUserId());

            if (exists) {
                skipped++;
                continue;
            }

            IncidentAssignee assignee = IncidentAssignee.builder()
                    .tenant(tenant)
                    .category(category)
                    .userId(req.getUserId())
                    .userName(req.getUserName())
                    .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                    .assignmentOrder(req.getAssignmentOrder() != null ? req.getAssignmentOrder() : 0)
                    .build();

            assignee = incidentAssigneeRepository.save(assignee);
            created.add(convertToAssigneeDTO(assignee, tenantId));
        }

        log.info("bulkInitAssigneeConfigs - success. tenantId={} created={} skipped={}",
                tenantId, created.size(), skipped);
        return created;
    }

    private IncidentAssigneeDTO convertToAssigneeDTO(IncidentAssignee assignee, String tenantId) {
        long activeCount = incidentRepository.countActiveIncidentsByAssignee(
                tenantId, assignee.getUserId());

        return IncidentAssigneeDTO.builder()
                .assigneeConfigId(assignee.getPkIncidentAssigneeId())
                .category(assignee.getCategory() != null ? assignee.getCategory().name() : null)
                .userId(assignee.getUserId())
                .userName(assignee.getUserName())
                .isActive(assignee.getIsActive())
                .assignmentOrder(assignee.getAssignmentOrder())
                .activeIncidentCount(activeCount)
                .createdAt(formatInstant(assignee.getCreatedAt()))
                .updatedAt(formatInstant(assignee.getUpdatedAt()))
                .build();
    }
}
