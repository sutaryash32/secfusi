package com.secufusion.events.controller;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Event;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.kafka.EventFlushScheduler;
import com.secufusion.events.kafka.EventKafkaProducer;
import com.secufusion.events.service.ActivitySummaryService;
import com.secufusion.events.service.EventService;
import com.secufusion.events.util.JwtUtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller that exposes endpoints to add and retrieve events.
 *
 * <p>Endpoints:
 * - POST /events : Accepts a batch of events to persist.
 * - GET  /events : Returns user events for a given tenant.
 * <p>
 * Logging is performed on method entry, success and error paths to aid debugging.
 */
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/events")
@Tag(name = "Event", description = "Event endpoints")
public class EventController {

    private final EventService eventService;
    private final ActivitySummaryService activitySummaryService;

    @Autowired
    private JwtUtl jwtUtl;

    @Autowired
    private EventKafkaProducer eventKafkaProducer;

    @Autowired
    private EventFlushScheduler eventFlushScheduler;

    public EventController(EventService eventService, ActivitySummaryService activitySummaryService) {
        this.eventService = eventService;
        this.activitySummaryService = activitySummaryService;
    }

    /**
     * Saves a batch of events supplied in the request body.
     *
     * @param request         http servlet request (used for trace headers / uri logging)
     * @param eventRequestDto payload containing events to persist
     * @return 200 OK on success, 500 on server error
     */
    @PostMapping
    @Operation(
            summary = "Add events",
            description = "Saves a batch of events supplied in the request body."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events added successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> addEvents(HttpServletRequest request, @RequestBody EventRequestDto eventRequestDto) {
        String requestUri = request != null ? request.getRequestURI() : "unknown";
        String requestId = request != null ? request.getHeader("X-Request-ID") : null;
        int eventCount = eventRequestDto != null && eventRequestDto.getEvents() != null ? eventRequestDto.getEvents().size() : 0;

        log.info("addEvents - entry. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount);

        if (eventRequestDto == null || eventRequestDto.getEvents() == null || eventCount == 0) {
            log.warn("addEvents - bad request: empty payload. uri={} requestId={}", requestUri, requestId);
            return ResponseEntity.badRequest().build();
        }

        try {
            eventService.saveAllEvents(request, eventRequestDto.getEvents());
            log.info("addEvents - success. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            log.error("addEvents - error while adding events. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/kafka")
    @Operation(
            summary = "Add events to Kafka",
            description = "Publishes a batch of events to Kafka for asynchronous processing."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Events accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> addEventsToKafka(
            HttpServletRequest request,
            @RequestBody EventRequestDto eventRequestDto) {

        // extract tracing info for logs
        String requestUri = request != null ? request.getRequestURI() : "unknown";
        String requestId = request != null ? request.getHeader("X-Request-ID") : null;
        int eventCount = eventRequestDto != null && eventRequestDto.getEvents() != null ? eventRequestDto.getEvents().size() : 0;

        log.info("addEventsToKafka - entry. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount);

        // validate payload
        if (eventRequestDto == null
                || eventRequestDto.getEvents() == null
                || eventRequestDto.getEvents().isEmpty()) {
            log.warn("addEventsToKafka - bad request: empty payload. uri={} requestId={}", requestUri, requestId);
            return ResponseEntity.badRequest().build();
        }

        try {
            // resolve tenant and user from JWT
            Tenant tenant = jwtUtl.getTenantFromRequest(request);
            String userName = jwtUtl.getPreferredUsernameFromRequest(request);
            log.debug("addEventsToKafka - tenant resolved tenantId={} user={}", tenant != null ? tenant.getTenantID() : "null", userName);

            // build and publish Kafka messages for each event
            eventRequestDto.getEvents().forEach(eventDto -> {
                String eventId = UUID.randomUUID().toString();
                EventKafkaMessage message = new EventKafkaMessage(
                        eventId,                             // id (idempotency token)
                        tenant.getTenantID(),                // tenant id
                        userName,                            // user
                        eventDto,                            // event payload
                        System.currentTimeMillis()           // occurredAt
                );

                try {
                    eventKafkaProducer.send(message);
                    log.debug("addEventsToKafka - message sent. eventId={} tenantId={}", eventId, tenant.getTenantID());
                } catch (Exception ex) {
                    // log send failure but continue with remaining messages
                    log.error("addEventsToKafka - failed to send message. eventId={} tenantId={}", eventId, tenant.getTenantID(), ex);
                }
            });

            log.info("addEventsToKafka - accepted for processing. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount);
            return ResponseEntity.accepted().build(); // async & safe
        } catch (Exception ex) {
            log.error("addEventsToKafka - unexpected error. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PostMapping("/kafka/sync")
    public ResponseEntity<Void> syncTenant(HttpServletRequest request) {

        // trace/log context
        String requestUri = request != null ? request.getRequestURI() : "unknown";
        String requestId = request != null ? request.getHeader("X-Request-ID") : null;
        log.info("syncTenant - entry. uri={} requestId={}", requestUri, requestId);

        try {
            // determine tenant to flush
            String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
            log.debug("syncTenant - flushing tenantId={}", tenantId);

            eventService.flushTenant(tenantId);

            log.info("syncTenant - accepted. tenantId={} uri={} requestId={}", tenantId, requestUri, requestId);
            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            log.error("syncTenant - error while flushing tenant. uri={} requestId={}", requestUri, requestId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Retrieves user events for the given tenant.
     *
     * @return list of user events (200) or appropriate error status
     */
    @Operation(summary = "Get user events", description = "Retrieves user events for the given tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User events retrieved successfully", content = @Content(schema = @Schema(implementation = UserEventsResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - missing or invalid tenantId", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<ResponseDto<List<UserEventsResponseDto>>> getUserEvents(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getUserEvents - entry. tenantId={}", tenantId);

        if (tenantId == null || tenantId.isBlank()) {
            log.warn("getUserEvents - bad request: tenantId is blank");
            return ResponseEntity.badRequest().build();
        }

        try {
            List<UserEventsResponseDto> userEvents = eventService.getUserEvents(tenantId);
            log.info("getUserEvents - success. tenantId={} resultCount={}", tenantId, userEvents != null ? userEvents.size() : 0);
            return ResponseEntity.ok(new ResponseDto<>(userEvents,String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getUserEvents - error while retrieving user events. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get tenant activity summary with aggregated statistics.
     */
    @GetMapping("/summary")
    @Operation(summary = "Get tenant activity summary",
            description = "Returns aggregated activity statistics for the tenant including device stats, " +
                    "event counts, active users, top domains, and policy violations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<TenantActivitySummaryDTO>> getTenantActivitySummary(
            HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getTenantActivitySummary - entry. tenantId={}", tenantId);

        try {
            TenantActivitySummaryDTO summary = activitySummaryService.getTenantActivitySummary(tenantId);
            log.info("getTenantActivitySummary - success. tenantId={}", tenantId);
            return ResponseEntity.ok(new ResponseDto<>(summary, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getTenantActivitySummary - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get events for a specific device with pagination.
     */
    @GetMapping("/device/{deviceId}")
    @Operation(summary = "Get device events",
            description = "Returns paginated list of events for a specific device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<EventDto>>> getDeviceEvents(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getDeviceEvents - entry. tenantId={} deviceId={} page={} size={}", tenantId, deviceId, page, size);

        try {
            Page<EventDto> events = eventService.getEventsByDevice(deviceId, page, size);
            log.info("getDeviceEvents - success. tenantId={} deviceId={} totalElements={}",
                    tenantId, deviceId, events.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getDeviceEvents - error. tenantId={} deviceId={}", tenantId, deviceId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get events for a specific device within a time range.
     */
    @GetMapping("/device/{deviceId}/range")
    @Operation(summary = "Get device events by time range",
            description = "Returns events for a specific device within a time range")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<EventDto>>> getDeviceEventsByRange(
            HttpServletRequest request,
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            @Parameter(description = "Start datetime (ISO format)") @RequestParam LocalDateTime start,
            @Parameter(description = "End datetime (ISO format)") @RequestParam LocalDateTime end) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getDeviceEventsByRange - entry. tenantId={} deviceId={} start={} end={}",
                tenantId, deviceId, start, end);

        try {
            List<EventDto> events = eventService.getEventsByDeviceAndTimeRange(deviceId, start, end);
            log.info("getDeviceEventsByRange - success. tenantId={} deviceId={} count={}",
                    tenantId, deviceId, events.size());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getDeviceEventsByRange - error. tenantId={} deviceId={}", tenantId, deviceId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get events for a specific user with pagination.
     */
    @GetMapping("/user/{userName}")
    @Operation(summary = "Get user events",
            description = "Returns paginated list of events for a specific user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<EventDto>>> getEventsByUser(
            HttpServletRequest request,
            @Parameter(description = "Username") @PathVariable String userName,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getEventsByUser - entry. tenantId={} userName={} page={} size={}", tenantId, userName, page, size);

        try {
            Page<EventDto> events = eventService.getEventsByUser(tenantId, userName, page, size);
            log.info("getEventsByUser - success. tenantId={} userName={} totalElements={}",
                    tenantId, userName, events.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getEventsByUser - error. tenantId={} userName={}", tenantId, userName, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    // ==================== IMPROVED EVENT ENDPOINTS ====================

    /**
     * Get all events with pagination and optional time range filter.
     */
    @GetMapping("/all")
    @Operation(summary = "Get all events with pagination",
            description = "Returns paginated list of all events with optional time range filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<EventDto>>> getAllEvents(
            HttpServletRequest request,
            @Parameter(description = "Start datetime (ISO format, optional)") @RequestParam(required = false) LocalDateTime start,
            @Parameter(description = "End datetime (ISO format, optional)") @RequestParam(required = false) LocalDateTime end,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getAllEvents - tenantId={} start={} end={} page={} size={}", tenantId, start, end, page, size);

        try {
            Page<EventDto> events = eventService.getAllEvents(tenantId, start, end, page, size);
            log.info("getAllEvents - success. tenantId={} totalElements={}", tenantId, events.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getAllEvents - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Search events by URL, title, or domain.
     */
    @GetMapping("/search")
    @Operation(summary = "Search events",
            description = "Search events by URL, title, or domain with pagination")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - missing search term"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Page<EventDto>>> searchEvents(
            HttpServletRequest request,
            @Parameter(description = "Search term (searches URL, title, domain)") @RequestParam String q,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("searchEvents - tenantId={} searchTerm={} page={} size={}", tenantId, q, page, size);

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ResponseDto<>(null, "Search term 'q' is required"));
        }

        try {
            Page<EventDto> events = eventService.searchEvents(tenantId, q, page, size);
            log.info("searchEvents - success. tenantId={} totalElements={}", tenantId, events.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("searchEvents - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get events with advanced filters.
     */
    @GetMapping("/filter")
    @Operation(
            summary = "Filter events",
            description = """
                    Filter events with multiple optional criteria. All parameters are optional and combinable.

                    **eventType values:**
                    - `WEBSITE_VISIT` — URL navigation / page load
                    - `TRACKING_ACTIVITY` — analytics / tracker detection
                    - `FILE_OPERATION` — generic file operation (legacy)
                    - `FILE_DOWNLOAD` — file download
                    - `FILE_UPLOAD` — file upload
                    - `FILE_PRINT` — print operation
                    - `FILE_CLIPBOARD_COPY` — copy to clipboard
                    - `FILE_CLIPBOARD_PASTE` — paste from clipboard
                    - `POLICY_VIOLATION` — DLP / policy block
                    - `SECURITY_THREAT` — malware, phishing, CSP violation
                    - `USER_BEHAVIOR` — copy/paste, screenshot, idle
                    - `SYSTEM_CONFIGURATION` — extension settings / policy sync

                    **fileOperationType values:** `DOWNLOAD`, `UPLOAD`, `PRINT`, `CLIPBOARD_COPY`, `CLIPBOARD_PASTE`

                    **Dashboard card examples:**
                    - Total downloads: `?eventType=FILE_DOWNLOAD`
                    - Total uploads: `?eventType=FILE_UPLOAD`
                    - Downloads blocked by DLP: `?eventType=FILE_DOWNLOAD&isBlocked=true`
                    - Uploads blocked by DLP: `?eventType=FILE_UPLOAD&isBlocked=true`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Filtered events retrieved successfully",
                    content = @Content(schema = @Schema(implementation = EventDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<ResponseDto<Page<EventDto>>> filterEvents(
            HttpServletRequest request,
            @Parameter(description = "Event type — one of: WEBSITE_VISIT, TRACKING_ACTIVITY, FILE_OPERATION, FILE_DOWNLOAD, FILE_UPLOAD, FILE_PRINT, FILE_CLIPBOARD_COPY, FILE_CLIPBOARD_PASTE, POLICY_VIOLATION, SECURITY_THREAT, USER_BEHAVIOR, SYSTEM_CONFIGURATION")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "Filter by username (exact match)")
            @RequestParam(required = false) String userName,
            @Parameter(description = "Filter by device ID")
            @RequestParam(required = false) String deviceId,
            @Parameter(description = "true = only policy violations, false = exclude policy violations")
            @RequestParam(required = false) Boolean isPolicyViolation,
            @Parameter(description = "true = only security events, false = exclude security events")
            @RequestParam(required = false) Boolean isSecurityEvent,
            @Parameter(description = "true = only blocked events (DLP blocks), false = only allowed events")
            @RequestParam(required = false) Boolean isBlocked,
            @Parameter(description = "File operation sub-type — one of: DOWNLOAD, UPLOAD, PRINT, CLIPBOARD_COPY, CLIPBOARD_PASTE. Use with eventType=FILE_OPERATION or alone.")
            @RequestParam(required = false) String fileOperationType,
            @Parameter(description = "Filter by event category (e.g. social, productivity)")
            @RequestParam(required = false) String category,
            @Parameter(description = "Start datetime — ISO 8601 format, e.g. 2025-01-01T00:00:00")
            @RequestParam(required = false) LocalDateTime start,
            @Parameter(description = "End datetime — ISO 8601 format, e.g. 2025-12-31T23:59:59")
            @RequestParam(required = false) LocalDateTime end,
            @Parameter(description = "Page number, 0-based (default: 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default: 20, max recommended: 100)")
            @RequestParam(defaultValue = "20") int size) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("filterEvents - tenantId={} eventType={} userName={} deviceId={} isPolicyViolation={} " +
                        "isSecurityEvent={} isBlocked={} fileOperationType={} category={} start={} end={}",
                tenantId, eventType, userName, deviceId, isPolicyViolation, isSecurityEvent,
                isBlocked, fileOperationType, category, start, end);

        try {
            // Convert eventType string to enum if provided
            com.secufusion.events.entity.EventType eventTypeEnum = null;
            if (eventType != null && !eventType.isBlank()) {
                try {
                    eventTypeEnum = com.secufusion.events.entity.EventType.valueOf(eventType.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid eventType: {}", eventType);
                }
            }

            Page<EventDto> events = eventService.getEventsWithFilters(tenantId, eventTypeEnum, userName,
                    deviceId, isPolicyViolation, isSecurityEvent, isBlocked, fileOperationType,
                    category, start, end, page, size);
            log.info("filterEvents - success. tenantId={} totalElements={}", tenantId, events.getTotalElements());
            return ResponseEntity.ok(new ResponseDto<>(events, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("filterEvents - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get total event count for the tenant.
     */
    @GetMapping("/count")
    @Operation(summary = "Get event count", description = "Returns total count of events for the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<Long>> getEventCount(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getEventCount - tenantId={}", tenantId);

        try {
            long count = eventService.getEventCount(tenantId);
            log.info("getEventCount - success. tenantId={} count={}", tenantId, count);
            return ResponseEntity.ok(new ResponseDto<>(count, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getEventCount - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get distinct categories for the tenant.
     */
    @GetMapping("/categories")
    @Operation(summary = "Get event categories", description = "Returns list of distinct event categories for the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categories retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<String>>> getCategories(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getCategories - tenantId={}", tenantId);

        try {
            List<String> categories = eventService.getDistinctCategories(tenantId);
            log.info("getCategories - success. tenantId={} count={}", tenantId, categories.size());
            return ResponseEntity.ok(new ResponseDto<>(categories, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getCategories - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }

    /**
     * Get distinct users who have events.
     */
    @GetMapping("/users")
    @Operation(summary = "Get event users", description = "Returns list of distinct users who have events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ResponseDto<List<String>>> getEventUsers(HttpServletRequest request) {

        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
        log.info("getEventUsers - tenantId={}", tenantId);

        try {
            List<String> users = eventService.getDistinctUserNames(tenantId);
            log.info("getEventUsers - success. tenantId={} count={}", tenantId, users.size());
            return ResponseEntity.ok(new ResponseDto<>(users, String.valueOf(HttpStatus.OK.value())));
        } catch (Exception ex) {
            log.error("getEventUsers - error. tenantId={}", tenantId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto<>(null, ex.getMessage()));
        }
    }
}