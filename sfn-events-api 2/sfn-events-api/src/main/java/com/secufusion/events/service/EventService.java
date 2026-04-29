package com.secufusion.events.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.EventDto;
import com.secufusion.events.dto.UserEventsResponseDto;
import com.secufusion.events.entity.Device;
import com.secufusion.events.entity.Event;
import com.secufusion.events.entity.EventInboxEntity;
import com.secufusion.events.entity.EventType;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.exception.EventException;
import com.secufusion.events.kafka.EventKafkaConsumer;
import com.secufusion.events.repository.DeviceRepository;
import com.secufusion.events.repository.EventInboxRepository;
import com.secufusion.events.repository.EventRepository;
import com.secufusion.events.repository.TenantRepository;
import com.secufusion.events.repository.UserRepository;
import com.secufusion.events.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service responsible for persisting and retrieving Event data.
 *
 * <p>Main responsibilities:
 * - Persist incoming events associated with the tenant and user extracted from the HTTP request (JWT).
 * - Retrieve events grouped by user for a given tenant identifier.
 * - Synchronize events produced to Kafka (or other message bus) into persistent storage.
 *
 * <p>All public operations log key lifecycle events to aid operational visibility and troubleshooting.
 */
@Service
@Slf4j
public class EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final DeviceRepository deviceRepository;
    private final JwtUtl jwtUtl;
    private final EventKafkaConsumer consumer;
    private final EventInboxRepository inboxRepository;
    private final ObjectMapper objectMapper;


    public EventService(EventRepository eventRepository,
                        UserRepository userRepository,
                        TenantRepository tenantRepository,
                        DeviceRepository deviceRepository,
                        JwtUtl jwtUtl,
                        EventKafkaConsumer consumer,
                        EventInboxRepository eventInboxRepository,
                        ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.jwtUtl = jwtUtl;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.deviceRepository = deviceRepository;
        this.consumer = consumer;
        this.inboxRepository = eventInboxRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Persist a batch of events associated with the tenant and user extracted from the request.
     * Transactional so either all events are saved or none.
     *
     * @param request current HTTP request (used to resolve tenant and user)
     * @param events  list of event DTOs to persist
     */
    @Transactional
    public void saveAllEvents(HttpServletRequest request, List<EventDto> events) {
        log.debug("Entered saveAllEvents(request={}, eventsCount={})",
                request != null ? request.getRequestURI() : "null-request",
                events != null ? events.size() : 0);

        if (events == null || events.isEmpty()) {
            log.debug("saveAllEvents called with empty or null events list; nothing to persist.");
            return;
        }

        if (request == null) {
            log.warn("HTTP request is null; cannot resolve tenant/user from JWT. Aborting saveAllEvents.");
            throw new com.secufusion.events.exception.EventException("Request is required to resolve tenant and user");
        }

        try {
            // Resolve tenant and user from JWT attached to the request.
            Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
            if (tenantFromRequest == null) {
                log.error("saveAllEvents: could not resolve tenant from JWT (azp not a tenant name and iss realm not found)");
                throw new EventException("Tenant context missing in token");
            }
            String preferredUsernameFromRequest = jwtUtl.getPreferredUsernameFromRequest(request);
            String tenantId = tenantFromRequest.getTenantID();

            log.debug("Preparing to persist {} events for tenant={} user={}", events.size(),
                    Objects.toString(tenantFromRequest), Objects.toString(preferredUsernameFromRequest));

            // Collect unique deviceIds from events and fetch devices in batch
            List<String> deviceIds = events.stream()
                    .map(EventDto::getDeviceId)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();

            // Build a map of deviceId -> Device for quick lookup
            Map<String, Device> deviceMap = new HashMap<>();
            if (!deviceIds.isEmpty()) {
                List<Device> devices = deviceRepository.findAllById(deviceIds);
                for (Device device : devices) {
                    // Only include devices that belong to this tenant
                    if (device.getTenantId() != null && device.getTenantId().equals(tenantId)) {
                        deviceMap.put(device.getDeviceId(), device);
                    }
                }
                log.debug("Found {} devices for {} deviceIds", deviceMap.size(), deviceIds.size());
            }

            // Convert DTOs to entities and link devices
            List<Event> entities = events.stream()
                    .map(eventDto -> {
                        Event event = Event.from(eventDto, tenantFromRequest, preferredUsernameFromRequest);
                        // Link device if deviceId is provided and device exists
                        if (eventDto.getDeviceId() != null && !eventDto.getDeviceId().isBlank()) {
                            Device device = deviceMap.get(eventDto.getDeviceId());
                            if (device != null) {
                                event.setDevice(device);
                            } else {
                                log.warn("Device not found for deviceId={} tenantId={}",
                                        eventDto.getDeviceId(), tenantId);
                            }
                        }
                        return event;
                    })
                    .collect(Collectors.toList());

            // Persist all events in a single batch call
            eventRepository.saveAll(entities);
            log.info("Persisted {} events for tenant={} user={}", entities.size(),
                    Objects.toString(tenantFromRequest), Objects.toString(preferredUsernameFromRequest));
        } catch (DataAccessException ex) {
            // Log DB access issues and rethrow domain-specific exception for upper layers
            log.error("Failed to persist events due to data access error; eventsCount={}", events.size(), ex);
            throw new EventException("Failed to persist events", ex);
        } catch (Exception ex) {
            // Catch-all to ensure unexpected errors are logged and wrapped
            log.error("Unexpected error while saving events; eventsCount={}", events.size(), ex);
            throw new EventException("Unexpected error while saving events", ex);
        } finally {
            log.trace("Exiting saveAllEvents");
        }
    }

    /**
     * Retrieve events grouped by user for the specified tenant id.
     *
     * @param tenantId external tenant identifier used to look up tenant and its users
     * @return list of UserEventsResponseDto containing user id and their events; empty list on error or when no data
     */
    @Transactional(readOnly = true)
    public List<UserEventsResponseDto> getUserEvents(String tenantId) {
        log.debug("Entered getUserEvents(tenantId={})", tenantId);

        if (tenantId == null) {
            log.debug("getUserEvents called with null tenantId; returning empty list.");
            return Collections.emptyList();
        }

        try {
            log.debug("Fetching tenant for tenantId={}", tenantId);
            Optional<Tenant> tenant = tenantRepository.findByTenantID(tenantId);
            if (tenant.isEmpty()) {
                log.warn("No tenant found for tenantId={}", tenantId);
                return Collections.emptyList();
            }

            Optional<List<Event>> eventsOpt = eventRepository.findByTenant(tenant.get());
            List<Event> events = eventsOpt.orElse(Collections.emptyList());
            if (events.isEmpty()) {
                log.debug("No events found for tenantId={}", tenantId);
                return Collections.emptyList();
            }

            // Group by userName from the Event entity (filter out events without a user)
            var grouped = events.stream()
                    .filter(e -> e.getUserName() != null)
                    .collect(Collectors.groupingBy(
                            Event::getUserName,
                            Collectors.mapping(this::convertToDto, Collectors.toList())
                    ));

            List<UserEventsResponseDto> response = grouped.entrySet().stream()
                    .map(e -> UserEventsResponseDto.builder()
                            .userName(e.getKey())
                            .userEvents(e.getValue())
                            .build())
                    .collect(Collectors.toList());

            log.info("Returning events for {} users for tenantId={}", response.size(), tenantId);
            return response;
        } catch (Exception ex) {
            log.error("Error while retrieving user events for tenantId={}", tenantId, ex);
            return Collections.emptyList();
        } finally {
            log.trace("Exiting getUserEvents(tenantId={})", tenantId);
        }
    }

    @Transactional
    public void flushTenant(String tenantId) {
        log.debug("[EVENT-FLUSH] Entering flushTenant(tenantId={})", tenantId);

        // Load up to 500 unprocessed inbox events for the tenant
        List<EventInboxEntity> inbox =
                inboxRepository.findTop500ByTenantIdAndProcessedFalse(tenantId);

        if (inbox.isEmpty()) {
            log.info("[EVENT-FLUSH] No events for tenant={}", tenantId);
            log.debug("[EVENT-FLUSH] Exiting flushTenant(tenantId={}) - nothing to do", tenantId);
            return;
        }

        log.info("[EVENT-FLUSH] Found {} inbox events for tenant={}", inbox.size(), tenantId);

        // Resolve tenant entity. If not found, log and throw.
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> {
                    log.error("[EVENT-FLUSH] Tenant not found for id={}", tenantId);
                    return new RuntimeException("Tenant not found: " + tenantId);
                });

        // Collect unique deviceIds from inbox events and fetch devices in batch
        List<String> deviceIds = inbox.stream()
                .map(e -> e.getEventPayload() != null ? e.getEventPayload().getDeviceId() : null)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        // Build a map of deviceId -> Device for quick lookup
        Map<String, Device> deviceMap = new HashMap<>();
        if (!deviceIds.isEmpty()) {
            List<Device> devices = deviceRepository.findAllById(deviceIds);
            for (Device device : devices) {
                if (device.getTenantId() != null && device.getTenantId().equals(tenantId)) {
                    deviceMap.put(device.getDeviceId(), device);
                }
            }
            log.debug("[EVENT-FLUSH] Found {} devices for {} deviceIds", deviceMap.size(), deviceIds.size());
        }

        // Convert inbox payloads to Event entities and link devices
        List<Event> events = inbox.stream()
                .map(e -> {
                    try {
                        EventDto dto = e.getEventPayload();
                        Event ev = Event.from(dto, tenant, e.getUserName());
                        // Link device if deviceId is provided
                        if (dto != null && dto.getDeviceId() != null && !dto.getDeviceId().isBlank()) {
                            Device device = deviceMap.get(dto.getDeviceId());
                            if (device != null) {
                                ev.setDevice(device);
                            }
                        }
                        log.trace("[EVENT-FLUSH] Converted inbox id={} user={} to Event", e.getEventId(), e.getUserName());
                        return ev;
                    } catch (Exception ex) {
                        log.error("[EVENT-FLUSH] Failed to convert inbox id={} payload to Event; aborting flush for tenant={}", e.getEventId(), tenantId, ex);
                        throw ex;
                    }
                })
                .toList();

        log.debug("[EVENT-FLUSH] Persisting {} events for tenant={}", events.size(), tenantId);
        eventRepository.saveAll(events);
        log.info("[EVENT-FLUSH] Persisted {} events for tenant={}", events.size(), tenantId);

        // Mark inbox records as processed
        List<String> ids = inbox.stream().map(EventInboxEntity::getEventId).toList();
        inboxRepository.markProcessed(ids);
        log.info("[EVENT-FLUSH] Marked {} inbox records processed for tenant={}", ids.size(), tenantId);

        log.info("[EVENT-FLUSH] Tenant={} flushed {} events", tenantId, events.size());
        log.debug("[EVENT-FLUSH] Exiting flushTenant(tenantId={})", tenantId);
    }

    /**
     * Get events for a specific device with pagination.
     *
     * @param deviceId the device identifier
     * @param page     page number (0-based)
     * @param size     page size
     * @return paginated list of events for the device
     */
    @Transactional(readOnly = true)
    public Page<EventDto> getEventsByDevice(String deviceId, int page, int size) {
        log.debug("getEventsByDevice - deviceId={} page={} size={}", deviceId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findByDevice_DeviceIdOrderByTimeStampDesc(deviceId, pageable)
                .map(this::convertToDto);
    }

    /**
     * Get events for a specific device within a time range.
     *
     * @param deviceId the device identifier
     * @param start    start datetime
     * @param end      end datetime
     * @return list of events for the device within the time range
     */
    @Transactional(readOnly = true)
    public List<EventDto> getEventsByDeviceAndTimeRange(String deviceId, LocalDateTime start, LocalDateTime end) {
        log.debug("getEventsByDeviceAndTimeRange - deviceId={} start={} end={}", deviceId, start, end);
        return eventRepository.findByDevice_DeviceIdAndTimeStampBetweenOrderByTimeStampDesc(deviceId, start, end)
                .stream()
                .map(this::convertToDto)
                .toList();
    }

    /**
     * Get events for a specific user with pagination.
     *
     * @param tenantId the tenant identifier
     * @param userName the username
     * @param page     page number (0-based)
     * @param size     page size
     * @return paginated list of events for the user
     */
    @Transactional(readOnly = true)
    public Page<EventDto> getEventsByUser(String tenantId, String userName, int page, int size) {
        log.debug("getEventsByUser - tenantId={} userName={} page={} size={}", tenantId, userName, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findByTenant_TenantIDAndUserNameOrderByTimeStampDesc(tenantId, userName, pageable)
                .map(this::convertToDto);
    }

    /**
     * Convert Event entity to EventDto.
     */
    private EventDto convertToDto(Event event) {
        EventDto dto = new EventDto();
        // Event identification
        dto.setId(event.getPkEventId());
        dto.setTenantId(event.getTenant() != null ? event.getTenant().getTenantID() : null);
        // Core fields
        dto.setUrl(event.getUrl());
        dto.setDomain(event.getDomain());
        dto.setTimeStamp(event.getTimeStamp() != null ? event.getTimeStamp().toString() : null);
        dto.setBrowserType(event.getBrowserType());
        dto.setDeviceType(event.getDeviceType());
        dto.setIpAddress(event.getIpAddress());
        dto.setLocation(event.getLocation());
        dto.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
        dto.setDeviceId(event.getDevice() != null ? event.getDevice().getDeviceId() : null);
        dto.setTitle(event.getTitle());
        dto.setDurationSeconds(event.getDurationSeconds());
        dto.setDetails(event.getDetails());
        dto.setCategory(event.getCategory());
        dto.setIsPolicyViolation(event.getIsPolicyViolation());
        dto.setPolicyRuleId(event.getPolicyRuleId());
        // File operation fields
        dto.setFileOperationType(event.getFileOperationType() != null ? event.getFileOperationType().name() : null);
        dto.setFileName(event.getFileName());
        dto.setFileSize(event.getFileSize());
        dto.setFileType(event.getFileType());
        dto.setIsBlocked(event.getIsBlocked());
        // Security threat fields
        dto.setIsSecurityEvent(event.getIsSecurityEvent());
        dto.setSeverity(event.getSeverity());
        dto.setThreatType(event.getThreatType());
        dto.setThreatLevel(event.getThreatLevel());
        dto.setActionTaken(event.getActionTaken());
        dto.setRiskLevel(event.getRiskLevel());
        // Policy matching fields
        dto.setPolicyName(event.getPolicyName());
        dto.setPolicyType(event.getPolicyType());
        dto.setFilterType(event.getFilterType());
        dto.setPatternType(event.getPatternType());
        dto.setMatchedPattern(event.getMatchedPattern());
        // Compliance fields
        dto.setComplianceImpact(event.getComplianceImpact());
        dto.setProcessingStatus(event.getProcessingStatus());
        // Device info
        if (event.getDevice() != null) {
            dto.setDeviceName(event.getDevice().getDeviceName());
            dto.setOsInfo(event.getDevice().getOsInfo());
            dto.setDeviceStatus(event.getDevice().getStatus() != null ? event.getDevice().getStatus().name() : null);
        }
        dto.setUserName(event.getUserName());

        // Device User info (auto-populated by database trigger)
        if (event.getDeviceUser() != null) {
            dto.setDeviceUserId(event.getDeviceUser().getPkDeviceUserId());
            dto.setDeviceUserEmail(event.getDeviceUser().getEmail());
            dto.setDeviceUserStatus(event.getDeviceUser().getStatus());
            dto.setLinkedToPortalUser(event.getDeviceUser().getPortalUserId() != null);
        }
        return dto;
    }

    // ==================== IMPROVED EVENT RETRIEVAL METHODS ====================

    /**
     * Get all events for a tenant with pagination and optional time range filter.
     *
     * @param tenantId the tenant identifier
     * @param start    optional start datetime
     * @param end      optional end datetime
     * @param page     page number (0-based)
     * @param size     page size
     * @return paginated list of events
     */
    @Transactional(readOnly = true)
    public Page<EventDto> getAllEvents(String tenantId, LocalDateTime start, LocalDateTime end,
                                       int page, int size) {
        log.debug("getAllEvents - tenantId={} start={} end={} page={} size={}",
                tenantId, start, end, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findAllEventsWithTimeRange(tenantId, start, end, pageable)
                .map(this::convertToDto);
    }

    /**
     * Search events by URL, title, or domain.
     *
     * @param tenantId   the tenant identifier
     * @param searchTerm the search term
     * @param page       page number (0-based)
     * @param size       page size
     * @return paginated list of matching events
     */
    @Transactional(readOnly = true)
    public Page<EventDto> searchEvents(String tenantId, String searchTerm, int page, int size) {
        log.debug("searchEvents - tenantId={} searchTerm={} page={} size={}",
                tenantId, searchTerm, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.searchEvents(tenantId, searchTerm, pageable)
                .map(this::convertToDto);
    }

    /**
     * Get events with advanced filters.
     *
     * @param tenantId          the tenant identifier
     * @param eventType         optional event type filter
     * @param userName          optional username filter
     * @param deviceId          optional device ID filter
     * @param isPolicyViolation optional policy violation filter
     * @param isSecurityEvent   optional security event filter
     * @param category          optional category filter
     * @param start             optional start datetime
     * @param end               optional end datetime
     * @param page              page number (0-based)
     * @param size              page size
     * @return paginated list of filtered events
     */
    @Transactional(readOnly = true)
    public Page<EventDto> getEventsWithFilters(String tenantId, EventType eventType,
                                               String userName, String deviceId,
                                               Boolean isPolicyViolation, Boolean isSecurityEvent,
                                               Boolean isBlocked, String fileOperationType,
                                               String category, LocalDateTime start,
                                               LocalDateTime end, int page, int size) {
        log.debug("getEventsWithFilters - tenantId={} eventType={} userName={} deviceId={} " +
                        "isPolicyViolation={} isSecurityEvent={} isBlocked={} fileOperationType={} category={} start={} end={}",
                tenantId, eventType, userName, deviceId, isPolicyViolation,
                isSecurityEvent, isBlocked, fileOperationType, category, start, end);
        Pageable pageable = PageRequest.of(page, size);
        // Convert EventType enum to String for native query
        String eventTypeStr = eventType != null ? eventType.name() : null;
        return eventRepository.findEventsWithFilters(tenantId, eventTypeStr, userName, deviceId,
                        isPolicyViolation, isSecurityEvent, isBlocked, fileOperationType,
                        category, start, end, pageable)
                .map(this::convertToDto);
    }

    /**
     * Get total event count for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return total event count
     */
    @Transactional(readOnly = true)
    public long getEventCount(String tenantId) {
        return eventRepository.countByTenant_TenantID(tenantId);
    }

    /**
     * Get distinct categories for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return list of distinct categories
     */
    @Transactional(readOnly = true)
    public List<String> getDistinctCategories(String tenantId) {
        return eventRepository.findDistinctCategories(tenantId);
    }

    /**
     * Get distinct users for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return list of distinct usernames
     */
    @Transactional(readOnly = true)
    public List<String> getDistinctUserNames(String tenantId) {
        return eventRepository.findDistinctUserNames(tenantId);
    }
}