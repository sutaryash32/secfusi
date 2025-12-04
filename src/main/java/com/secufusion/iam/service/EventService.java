package com.secufusion.iam.service;

import com.secufusion.iam.dto.EventDto;
import com.secufusion.iam.dto.UserEventsResponseDto;
import com.secufusion.iam.entity.Event;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.entity.User;
import com.secufusion.iam.exception.EventException;
import com.secufusion.iam.repository.EventRepository;
import com.secufusion.iam.repository.TenantRepository;
import com.secufusion.iam.repository.UserRepository;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service responsible for persisting and retrieving Event data.
 * Added logging for operational visibility and comments for maintainability.
 */
@Service
@Slf4j
public class EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtUtl jwtUtl;

    public EventService(EventRepository eventRepository, UserRepository userRepository, TenantRepository tenantRepository, JwtUtl jwtUtl) {
        this.eventRepository = eventRepository;
        this.jwtUtl = jwtUtl;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
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
        if (events == null || events.isEmpty()) {
            log.debug("saveAllEvents called with empty or null events list; nothing to persist.");
            return;
        }
        try {
            // Resolve tenant and user from JWT attached to the request.
            Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
            User user = jwtUtl.getUserFromRequest(request);

            log.debug("Preparing to persist {} events for tenant={} user={}", events.size(),
                    Objects.toString(tenantFromRequest), Objects.toString(user));

            // Convert DTOs to entities and stamp tenant/user
            List<Event> entities = events.stream()
                    .map(eventDto -> Event.from(eventDto, tenantFromRequest, user))
                    .collect(Collectors.toList());

            // Persist all events in a single batch call
            eventRepository.saveAll(entities);
            log.info("Persisted {} events for tenant={} user={}", entities.size(),
                    Objects.toString(tenantFromRequest), Objects.toString(user));
        } catch (DataAccessException ex) {
            // Log DB access issues and rethrow domain-specific exception for upper layers
            log.error("Failed to persist events due to data access error", ex);
            throw new EventException("Failed to persist events", ex);
        } catch (Exception ex) {
            // Catch-all to ensure unexpected errors are logged and wrapped
            log.error("Unexpected error while saving events", ex);
            throw new EventException("Unexpected error while saving events", ex);
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

            // Find users that belong to the tenant
            List<User> users = userRepository.findByTenant(tenant.get());
            log.debug("Found {} users for tenantId={}", users.size(), tenantId);
            if (users.isEmpty()) {
                return Collections.emptyList();
            }

            // For each user, fetch their events and map to DTOs. Filter out users without events.
            List<UserEventsResponseDto> response = users.stream()
                    .map(user -> {
                        Optional<List<Event>> userEventsOpt = eventRepository.findByUser(user);
                        List<Event> userEvents = userEventsOpt.orElse(Collections.emptyList());

                        if (userEvents.isEmpty()) {
                            // Skip users with no events to keep response compact
                            log.debug("User {} has no events; skipping.", Objects.toString(user));
                            return null;
                        }

                        List<EventDto> eventDtos = userEvents.stream()
                                .map(Event::toDto)
                                .collect(Collectors.toList());

                        log.debug("User {} has {} events", Objects.toString(user), eventDtos.size());

                        return UserEventsResponseDto.builder()
                                .userEvents(eventDtos)
                                .userId(user.getPkUserId())
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("Returning events for {} users for tenantId={}", response.size(), tenantId);
            return response;
        } catch (Exception ex) {
            // Log the error (tenantId is useful context) and return empty list to preserve API stability
            log.error("Error while retrieving user events for tenantId={}", tenantId, ex);
            return Collections.emptyList();
        }
    }
}