//package com.secufusion.iam.controller;
//
//import com.secufusion.iam.dto.EventRequestDto;
//import com.secufusion.iam.dto.UserEventsResponseDto;
//import com.secufusion.iam.service.EventService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import io.swagger.v3.oas.annotations.responses.ApiResponses;
//import io.swagger.v3.oas.annotations.responses.ApiResponse;
//import io.swagger.v3.oas.annotations.media.Content;
//import io.swagger.v3.oas.annotations.media.Schema;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
///**
// * REST controller that exposes endpoints to add and retrieve events.
// *
// * <p>Endpoints:
// * - POST /events : Accepts a batch of events to persist.
// * - GET  /events : Returns user events for a given tenant.
// * <p>
// * Logging is performed on method entry, success and error paths to aid debugging.
// */
//@Slf4j
//@CrossOrigin(origins = "*", allowedHeaders = "*")
//@RestController
//@RequestMapping("/events")
//@Tag(name = "Event", description = "Event endpoints")
//public class EventController {
//
//    private final EventService eventService;
//
//    public EventController(EventService eventService) {
//        this.eventService = eventService;
//    }
//
//    /**
//     * Saves a batch of events supplied in the request body.
//     *
//     * @param request         http servlet request (used for trace headers / uri logging)
//     * @param eventRequestDto payload containing events to persist
//     * @return 200 OK on success, 500 on server error
//     */
//    @Operation(summary = "Add events", description = "Saves a batch of events supplied in the request body.")
//    @io.swagger.v3.oas.annotations.parameters.RequestBody(
//            description = "Event request payload containing an array of events",
//            required = true,
//            content = @Content(schema = @Schema(implementation = EventRequestDto.class))
//    )
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "Events added successfully", content = @Content),
//            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content),
//            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
//    })
//    @PostMapping
//    public ResponseEntity<Void> addEvents(HttpServletRequest request, @RequestBody EventRequestDto eventRequestDto) {
//        String requestUri = request != null ? request.getRequestURI() : "unknown";
//        String requestId = request != null ? request.getHeader("X-Request-ID") : null;
//        int eventCount = eventRequestDto != null && eventRequestDto.getEvents() != null ? eventRequestDto.getEvents().size() : 0;
//
//        log.info("addEvents - entry. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount);
//
//        if (eventRequestDto == null || eventRequestDto.getEvents() == null || eventCount == 0) {
//            log.warn("addEvents - bad request: empty payload. uri={} requestId={}", requestUri, requestId);
//            return ResponseEntity.badRequest().build();
//        }
//
//        try {
//            eventService.saveAllEvents(request, eventRequestDto.getEvents());
//            log.info("addEvents - success. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount);
//            return ResponseEntity.ok().build();
//        } catch (Exception ex) {
//            log.error("addEvents - error while adding events. uri={} requestId={} eventCount={}", requestUri, requestId, eventCount, ex);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }
//
//    /**
//     * Retrieves user events for the given tenant.
//     *
//     * @param tenantId tenant identifier for which user events are requested
//     * @return list of user events (200) or appropriate error status
//     */
//    @Operation(summary = "Get user events", description = "Retrieves user events for the given tenant.")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "User events retrieved successfully", content = @Content(schema = @Schema(implementation = UserEventsResponseDto.class))),
//            @ApiResponse(responseCode = "400", description = "Bad request - missing or invalid tenantId", content = @Content),
//            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
//    })
//    @GetMapping
//    public ResponseEntity<List<UserEventsResponseDto>> getUserEvents(
//            @Parameter(description = "Tenant identifier", required = true) @RequestParam(required = true) String tenantId) {
//
//        log.info("getUserEvents - entry. tenantId={}", tenantId);
//
//        if (tenantId == null || tenantId.isBlank()) {
//            log.warn("getUserEvents - bad request: tenantId is blank");
//            return ResponseEntity.badRequest().build();
//        }
//
//        try {
//            List<UserEventsResponseDto> userEvents = eventService.getUserEvents(tenantId);
//            log.info("getUserEvents - success. tenantId={} resultCount={}", tenantId, userEvents != null ? userEvents.size() : 0);
//            return ResponseEntity.ok(userEvents);
//        } catch (Exception ex) {
//            log.error("getUserEvents - error while retrieving user events. tenantId={}", tenantId, ex);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }
//}