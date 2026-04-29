package com.secufusion.events.service;

import com.secufusion.events.dto.EventDto;
import com.secufusion.events.dto.UserEventsResponseDto;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.EventException;
import com.secufusion.events.kafka.EventKafkaConsumer;
import com.secufusion.events.repository.*;
import com.secufusion.events.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventService Tests")
class EventServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────
    @Mock private EventRepository           eventRepository;
    @Mock private UserRepository            userRepository;
    @Mock private TenantRepository          tenantRepository;
    @Mock private DeviceRepository          deviceRepository;
    @Mock private JwtUtl                    jwtUtl;
    @Mock private EventKafkaConsumer        consumer;
    @Mock private EventInboxRepository      inboxRepository;
    @Mock private HttpServletRequest        mockRequest;

    @InjectMocks
    private EventService eventService;

    // ── Common test data ─────────────────────────────────────────────────────
    private static final String TENANT_ID   = "tenant-001";
    private static final String USERNAME    = "jane.doe";
    private static final String DEVICE_ID   = "device-001";
    private static final String TIMESTAMP   = "2024-01-15T10:30:00";

    private Tenant    mockTenant;
    private Device    mockDevice;
    private EventDto  sampleDto;
    private Event     sampleEvent;

    @BeforeEach
    void setUp() {
        mockTenant = new Tenant();
        mockTenant.setTenantID(TENANT_ID);
        mockTenant.setTenantName("Test Corp");

        mockDevice = new Device();
        mockDevice.setDeviceId(DEVICE_ID);
        mockDevice.setTenantId(TENANT_ID);
        mockDevice.setDeviceName("Jane's Laptop");

        sampleDto = new EventDto();
        sampleDto.setUrl("https://example.com/page");
        sampleDto.setTimeStamp(TIMESTAMP);
        sampleDto.setBrowserType("Chrome");
        sampleDto.setDeviceType("Desktop");
        sampleDto.setEventType("WEBSITE_VISIT");
        sampleDto.setDeviceId(DEVICE_ID);
        sampleDto.setTitle("Example Page");
        sampleDto.setIpAddress("192.168.1.1");
        sampleDto.setCategory("General");
        sampleDto.setProcessingStatus("Pending");

        sampleEvent = Event.builder()
                .pkEventId("evt-001")
                .url("https://example.com/page")
                .timeStamp(LocalDateTime.parse(TIMESTAMP))
                .tenant(mockTenant)
                .userName(USERNAME)
                .browserType("Chrome")
                .eventType(EventType.WEBSITE_VISIT)
                .build();
    }

    // ── Helper: build EventDto ────────────────────────────────────────────────
    private EventDto buildDto(String url, String deviceId) {
        EventDto dto = new EventDto();
        dto.setUrl(url);
        dto.setTimeStamp(TIMESTAMP);
        dto.setEventType("WEBSITE_VISIT");
        dto.setDeviceId(deviceId);
        dto.setProcessingStatus("Pending");
        return dto;
    }

    // =========================================================================
    // saveAllEvents
    // =========================================================================

    @Nested
    @DisplayName("saveAllEvents")
    class SaveAllEvents {

        @Test
        @DisplayName("Happy Path — events persisted successfully with device linked")
        void happyPath_eventsPersisted_withDevice() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(deviceRepository.findAllById(List.of(DEVICE_ID)))
                    .thenReturn(List.of(mockDevice));
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            assertDoesNotThrow(() ->
                    eventService.saveAllEvents(mockRequest, List.of(sampleDto)));

            // ASSERT
            ArgumentCaptor<List<Event>> captor = ArgumentCaptor.forClass(List.class);
            verify(eventRepository).saveAll(captor.capture());
            List<Event> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertEquals("https://example.com/page", saved.get(0).getUrl());
            assertEquals(USERNAME, saved.get(0).getUserName());
            assertNotNull(saved.get(0).getDevice()); // device was linked
        }

        @Test
        @DisplayName("Happy Path — multiple events persisted in batch")
        void happyPath_multipleEventsPersisted() {
            // ARRANGE
            EventDto dto2 = buildDto("https://other.com", DEVICE_ID);
            List<EventDto> dtos = List.of(sampleDto, dto2);

            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(deviceRepository.findAllById(anyList())).thenReturn(List.of(mockDevice));
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            assertDoesNotThrow(() -> eventService.saveAllEvents(mockRequest, dtos));

            // ASSERT
            ArgumentCaptor<List<Event>> captor = ArgumentCaptor.forClass(List.class);
            verify(eventRepository).saveAll(captor.capture());
            assertEquals(2, captor.getValue().size());
        }

        @Test
        @DisplayName("Happy Path — event without deviceId persisted without device link")
        void happyPath_noDeviceId_eventPersistedWithoutDevice() {
            // ARRANGE
            EventDto dtoNoDevice = buildDto("https://example.com", null);
            dtoNoDevice.setDeviceId(null);

            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            assertDoesNotThrow(() -> eventService.saveAllEvents(mockRequest, List.of(dtoNoDevice)));

            // ASSERT
            verify(deviceRepository, never()).findAllById(anyList());
            ArgumentCaptor<List<Event>> captor = ArgumentCaptor.forClass(List.class);
            verify(eventRepository).saveAll(captor.capture());
            assertNull(captor.getValue().get(0).getDevice());
        }

        @Test
        @DisplayName("Happy Path — deviceId provided but device not found; event saved without device")
        void happyPath_deviceNotFound_eventSavedWithoutDevice() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(deviceRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            assertDoesNotThrow(() -> eventService.saveAllEvents(mockRequest, List.of(sampleDto)));

            // ASSERT
            ArgumentCaptor<List<Event>> captor = ArgumentCaptor.forClass(List.class);
            verify(eventRepository).saveAll(captor.capture());
            assertNull(captor.getValue().get(0).getDevice());
        }

        @Test
        @DisplayName("Happy Path — device belongs to different tenant; not linked")
        void happyPath_deviceWrongTenant_notLinked() {
            // ARRANGE
            Device wrongTenantDevice = new Device();
            wrongTenantDevice.setDeviceId(DEVICE_ID);
            wrongTenantDevice.setTenantId("other-tenant");

            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(deviceRepository.findAllById(anyList())).thenReturn(List.of(wrongTenantDevice));
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            assertDoesNotThrow(() -> eventService.saveAllEvents(mockRequest, List.of(sampleDto)));

            // ASSERT — device from wrong tenant must not be linked
            ArgumentCaptor<List<Event>> captor = ArgumentCaptor.forClass(List.class);
            verify(eventRepository).saveAll(captor.capture());
            assertNull(captor.getValue().get(0).getDevice());
        }

        @Test
        @DisplayName("Happy Path — duplicate deviceIds batched into single DB query")
        void happyPath_duplicateDeviceIds_singleQuery() {
            // ARRANGE
            EventDto dto2 = buildDto("https://other.com", DEVICE_ID); // same deviceId
            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(deviceRepository.findAllById(List.of(DEVICE_ID))).thenReturn(List.of(mockDevice));
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            assertDoesNotThrow(() ->
                    eventService.saveAllEvents(mockRequest, List.of(sampleDto, dto2)));

            // ASSERT — only one findAllById call with deduplicated IDs
            verify(deviceRepository, times(1)).findAllById(anyList());
        }

        // ── Sad path ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("Sad Path — null events list returns early without saving")
        void sadPath_nullEvents_earlyReturn() {
            // ACT
            assertDoesNotThrow(() -> eventService.saveAllEvents(mockRequest, null));

            // ASSERT
            verifyNoInteractions(eventRepository);
            verifyNoInteractions(jwtUtl);
        }

        @Test
        @DisplayName("Sad Path — empty events list returns early without saving")
        void sadPath_emptyEvents_earlyReturn() {
            // ACT
            assertDoesNotThrow(() ->
                    eventService.saveAllEvents(mockRequest, Collections.emptyList()));

            // ASSERT
            verifyNoInteractions(eventRepository);
            verifyNoInteractions(jwtUtl);
        }

        @Test
        @DisplayName("Sad Path — null request throws EventException")
        void sadPath_nullRequest_throwsEventException() {
            // ACT + ASSERT
            assertThrows(EventException.class, () ->
                    eventService.saveAllEvents(null, List.of(sampleDto)));

            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Sad Path — tenant not resolved from JWT throws EventException")
        void sadPath_tenantNotResolved_throwsEventException() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(null);

            // ACT + ASSERT
            assertThrows(EventException.class, () ->
                    eventService.saveAllEvents(mockRequest, List.of(sampleDto)));

            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Sad Path — DataAccessException from saveAll throws EventException")
        void sadPath_dataAccessException_throwsEventException() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(deviceRepository.findAllById(anyList())).thenReturn(List.of(mockDevice));
            when(eventRepository.saveAll(anyList()))
                    .thenThrow(new DataIntegrityViolationException("Constraint violation"));

            // ACT + ASSERT
            assertThrows(EventException.class, () ->
                    eventService.saveAllEvents(mockRequest, List.of(sampleDto)));
        }

        @Test
        @DisplayName("Sad Path — unexpected RuntimeException from saveAll throws EventException")
        void sadPath_unexpectedException_throwsEventException() {
            // ARRANGE
            when(jwtUtl.getTenantFromRequest(mockRequest)).thenReturn(mockTenant);
            when(jwtUtl.getPreferredUsernameFromRequest(mockRequest)).thenReturn(USERNAME);
            when(deviceRepository.findAllById(anyList())).thenReturn(List.of(mockDevice));
            when(eventRepository.saveAll(anyList()))
                    .thenThrow(new RuntimeException("Unexpected DB failure"));

            // ACT + ASSERT
            assertThrows(EventException.class, () ->
                    eventService.saveAllEvents(mockRequest, List.of(sampleDto)));
        }
    }

    // =========================================================================
    // getUserEvents
    // =========================================================================

    @Nested
    @DisplayName("getUserEvents")
    class GetUserEvents {

        @Test
        @DisplayName("Happy Path — events grouped by user and returned")
        void happyPath_eventsGroupedByUser() {
            // ARRANGE
            Event event1 = Event.builder()
                    .pkEventId("evt-001")
                    .url("https://example.com")
                    .timeStamp(LocalDateTime.parse(TIMESTAMP))
                    .tenant(mockTenant)
                    .userName(USERNAME)
                    .eventType(EventType.WEBSITE_VISIT)
                    .build();

            Event event2 = Event.builder()
                    .pkEventId("evt-002")
                    .url("https://other.com")
                    .timeStamp(LocalDateTime.parse(TIMESTAMP))
                    .tenant(mockTenant)
                    .userName("other.user")
                    .eventType(EventType.WEBSITE_VISIT)
                    .build();

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(eventRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(List.of(event1, event2)));

            // ACT
            List<UserEventsResponseDto> result = eventService.getUserEvents(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(2, result.size()); // two distinct users
            assertTrue(result.stream().anyMatch(r -> r.getUserName().equals(USERNAME)));
            assertTrue(result.stream().anyMatch(r -> r.getUserName().equals("other.user")));
        }

        @Test
        @DisplayName("Happy Path — multiple events for same user grouped together")
        void happyPath_multipleEventsForSameUser_grouped() {
            // ARRANGE
            Event e1 = Event.builder().pkEventId("e1").url("https://a.com")
                    .timeStamp(LocalDateTime.parse(TIMESTAMP))
                    .tenant(mockTenant).userName(USERNAME)
                    .eventType(EventType.WEBSITE_VISIT).build();
            Event e2 = Event.builder().pkEventId("e2").url("https://b.com")
                    .timeStamp(LocalDateTime.parse(TIMESTAMP))
                    .tenant(mockTenant).userName(USERNAME)
                    .eventType(EventType.WEBSITE_VISIT).build();

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(eventRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(List.of(e1, e2)));

            // ACT
            List<UserEventsResponseDto> result = eventService.getUserEvents(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size()); // single user
            assertEquals(2, result.get(0).getUserEvents().size()); // two events
        }

        @Test
        @DisplayName("Happy Path — events with null userName are filtered out")
        void happyPath_nullUserNameEvents_filteredOut() {
            // ARRANGE
            Event eventWithUser = Event.builder().pkEventId("e1").url("https://a.com")
                    .timeStamp(LocalDateTime.parse(TIMESTAMP))
                    .tenant(mockTenant).userName(USERNAME)
                    .eventType(EventType.WEBSITE_VISIT).build();
            Event eventNoUser = Event.builder().pkEventId("e2").url("https://b.com")
                    .timeStamp(LocalDateTime.parse(TIMESTAMP))
                    .tenant(mockTenant).userName(null) // no user
                    .eventType(EventType.WEBSITE_VISIT).build();

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(eventRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(List.of(eventWithUser, eventNoUser)));

            // ACT
            List<UserEventsResponseDto> result = eventService.getUserEvents(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size()); // only the user with non-null userName
            assertEquals(USERNAME, result.get(0).getUserName());
        }

        @Test
        @DisplayName("Sad Path — null tenantId returns empty list")
        void sadPath_nullTenantId_returnsEmpty() {
            // ACT
            List<UserEventsResponseDto> result = eventService.getUserEvents(null);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verifyNoInteractions(tenantRepository);
        }

        @Test
        @DisplayName("Sad Path — tenant not found returns empty list")
        void sadPath_tenantNotFound_returnsEmpty() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID)).thenReturn(Optional.empty());

            // ACT
            List<UserEventsResponseDto> result = eventService.getUserEvents(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Sad Path — no events for tenant returns empty list")
        void sadPath_noEvents_returnsEmpty() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(eventRepository.findByTenant(mockTenant))
                    .thenReturn(Optional.of(Collections.emptyList()));

            // ACT
            List<UserEventsResponseDto> result = eventService.getUserEvents(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Sad Path — repository throws exception returns empty list")
        void sadPath_repositoryThrows_returnsEmpty() {
            // ARRANGE
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenThrow(new RuntimeException("DB down"));

            // ACT — exception caught internally, returns empty list
            List<UserEventsResponseDto> result = eventService.getUserEvents(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // flushTenant
    // =========================================================================

    @Nested
    @DisplayName("flushTenant")
    class FlushTenant {

        @Test
        @DisplayName("Happy Path — inbox events flushed and marked processed")
        void happyPath_inboxFlushed_markedProcessed() {
            // ARRANGE
            EventDto payload = buildDto("https://example.com", DEVICE_ID);
            EventInboxEntity inbox = mock(EventInboxEntity.class);
            when(inbox.getEventId()).thenReturn("inbox-001");
            when(inbox.getUserName()).thenReturn(USERNAME);
            when(inbox.getEventPayload()).thenReturn(payload);

            when(inboxRepository.findTop500ByTenantIdAndProcessedFalse(TENANT_ID))
                    .thenReturn(List.of(inbox));
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(deviceRepository.findAllById(anyList()))
                    .thenReturn(List.of(mockDevice));
            when(eventRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(inboxRepository).markProcessed(anyList());

            // ACT
            assertDoesNotThrow(() -> eventService.flushTenant(TENANT_ID));

            // ASSERT
            verify(eventRepository).saveAll(anyList());
            verify(inboxRepository).markProcessed(List.of("inbox-001"));
        }

        @Test
        @DisplayName("Happy Path — empty inbox returns early with no work done")
        void happyPath_emptyInbox_earlyReturn() {
            // ARRANGE
            when(inboxRepository.findTop500ByTenantIdAndProcessedFalse(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            assertDoesNotThrow(() -> eventService.flushTenant(TENANT_ID));

            // ASSERT
            verifyNoInteractions(tenantRepository);
            verifyNoInteractions(eventRepository);
        }

        @Test
        @DisplayName("Happy Path — inbox event with null deviceId flushed without device link")
        void happyPath_nullDeviceId_flushedWithoutDevice() {
            // ARRANGE
            EventDto payload = buildDto("https://example.com", null);
            payload.setDeviceId(null);
            EventInboxEntity inbox = mock(EventInboxEntity.class);
            when(inbox.getEventId()).thenReturn("inbox-001");
            when(inbox.getUserName()).thenReturn(USERNAME);
            when(inbox.getEventPayload()).thenReturn(payload);

            when(inboxRepository.findTop500ByTenantIdAndProcessedFalse(TENANT_ID))
                    .thenReturn(List.of(inbox));
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(eventRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(inboxRepository).markProcessed(anyList());

            // ACT
            assertDoesNotThrow(() -> eventService.flushTenant(TENANT_ID));

            // ASSERT
            verify(deviceRepository, never()).findAllById(anyList());
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws RuntimeException")
        void sadPath_tenantNotFound_throwsException() {
            // ARRANGE
            EventInboxEntity inbox = mock(EventInboxEntity.class);
            lenient().when(inbox.getEventPayload()).thenReturn(buildDto("https://example.com", null));
            lenient().when(inboxRepository.findTop500ByTenantIdAndProcessedFalse(TENANT_ID))
                    .thenReturn(List.of(inbox));
            lenient().when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThrows(RuntimeException.class, () -> eventService.flushTenant(TENANT_ID));
            verifyNoInteractions(eventRepository);
        }
    }

    // =========================================================================
    // getEventsByDevice
    // =========================================================================

    @Nested
    @DisplayName("getEventsByDevice")
    class GetEventsByDevice {

        @Test
        @DisplayName("Happy Path — paginated events returned for device")
        void happyPath_paginatedEventsReturned() {
            // ARRANGE
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByDevice_DeviceIdOrderByTimeStampDesc(
                    eq(DEVICE_ID), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<EventDto> result = eventService.getEventsByDevice(DEVICE_ID, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("https://example.com/page", result.getContent().get(0).getUrl());
        }

        @Test
        @DisplayName("Happy Path — empty page returned when no events for device")
        void happyPath_noEvents_emptyPage() {
            // ARRANGE
            Page<Event> emptyPage = new PageImpl<>(Collections.emptyList());
            when(eventRepository.findByDevice_DeviceIdOrderByTimeStampDesc(
                    eq(DEVICE_ID), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // ACT
            Page<EventDto> result = eventService.getEventsByDevice(DEVICE_ID, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    // =========================================================================
    // getEventsByDeviceAndTimeRange
    // =========================================================================

    @Nested
    @DisplayName("getEventsByDeviceAndTimeRange")
    class GetEventsByDeviceAndTimeRange {

        @Test
        @DisplayName("Happy Path — events within time range returned")
        void happyPath_eventsWithinRange_returned() {
            // ARRANGE
            LocalDateTime start = LocalDateTime.parse(TIMESTAMP).minusDays(1);
            LocalDateTime end   = LocalDateTime.parse(TIMESTAMP).plusDays(1);

            when(eventRepository.findByDevice_DeviceIdAndTimeStampBetweenOrderByTimeStampDesc(
                    eq(DEVICE_ID), eq(start), eq(end)))
                    .thenReturn(List.of(sampleEvent));

            // ACT
            List<EventDto> result = eventService.getEventsByDeviceAndTimeRange(DEVICE_ID, start, end);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("https://example.com/page", result.get(0).getUrl());
        }

        @Test
        @DisplayName("Happy Path — empty list when no events in range")
        void happyPath_noEventsInRange_emptyList() {
            // ARRANGE
            LocalDateTime start = LocalDateTime.parse(TIMESTAMP).minusDays(1);
            LocalDateTime end   = LocalDateTime.parse(TIMESTAMP).plusDays(1);

            when(eventRepository.findByDevice_DeviceIdAndTimeStampBetweenOrderByTimeStampDesc(
                    eq(DEVICE_ID), eq(start), eq(end)))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<EventDto> result = eventService.getEventsByDeviceAndTimeRange(DEVICE_ID, start, end);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // getEventsByUser
    // =========================================================================

    @Nested
    @DisplayName("getEventsByUser")
    class GetEventsByUser {

        @Test
        @DisplayName("Happy Path — paginated events returned for user")
        void happyPath_paginatedEventsForUser() {
            // ARRANGE
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.findByTenant_TenantIDAndUserNameOrderByTimeStampDesc(
                    eq(TENANT_ID), eq(USERNAME), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<EventDto> result = eventService.getEventsByUser(TENANT_ID, USERNAME, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(USERNAME, result.getContent().get(0).getUserName());
        }

        @Test
        @DisplayName("Happy Path — empty page when no events for user")
        void happyPath_noEvents_emptyPage() {
            // ARRANGE
            Page<Event> emptyPage = new PageImpl<>(Collections.emptyList());
            when(eventRepository.findByTenant_TenantIDAndUserNameOrderByTimeStampDesc(
                    eq(TENANT_ID), eq(USERNAME), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // ACT
            Page<EventDto> result = eventService.getEventsByUser(TENANT_ID, USERNAME, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    // =========================================================================
    // getAllEvents
    // =========================================================================

    @Nested
    @DisplayName("getAllEvents")
    class GetAllEvents {

        @Test
        @DisplayName("Happy Path — paginated events returned with time range")
        void happyPath_eventsWithTimeRange() {
            // ARRANGE
            LocalDateTime start = LocalDateTime.parse(TIMESTAMP).minusDays(7);
            LocalDateTime end   = LocalDateTime.parse(TIMESTAMP);
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));

            when(eventRepository.findAllEventsWithTimeRange(
                    eq(TENANT_ID), eq(start), eq(end), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<EventDto> result = eventService.getAllEvents(TENANT_ID, start, end, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Happy Path — null time range passed through to repository")
        void happyPath_nullTimeRange_passedThrough() {
            // ARRANGE
            Page<Event> page = new PageImpl<>(Collections.emptyList());
            when(eventRepository.findAllEventsWithTimeRange(
                    eq(TENANT_ID), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<EventDto> result = eventService.getAllEvents(TENANT_ID, null, null, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    // =========================================================================
    // searchEvents
    // =========================================================================

    @Nested
    @DisplayName("searchEvents")
    class SearchEvents {

        @Test
        @DisplayName("Happy Path — matching events returned for search term")
        void happyPath_matchingEventsReturned() {
            // ARRANGE
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));
            when(eventRepository.searchEvents(eq(TENANT_ID), eq("example"), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<EventDto> result = eventService.searchEvents(TENANT_ID, "example", 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Happy Path — no matches returns empty page")
        void happyPath_noMatches_emptyPage() {
            // ARRANGE
            Page<Event> emptyPage = new PageImpl<>(Collections.emptyList());
            when(eventRepository.searchEvents(eq(TENANT_ID), eq("xyz"), any(Pageable.class)))
                    .thenReturn(emptyPage);

            // ACT
            Page<EventDto> result = eventService.searchEvents(TENANT_ID, "xyz", 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    // =========================================================================
    // getEventsWithFilters
    // =========================================================================

    @Nested
    @DisplayName("getEventsWithFilters")
    class GetEventsWithFilters {

        @Test
        @DisplayName("Happy Path — filtered events returned with all filters set")
        void happyPath_allFilters_eventsReturned() {
            // ARRANGE
            LocalDateTime start = LocalDateTime.parse(TIMESTAMP).minusDays(1);
            LocalDateTime end   = LocalDateTime.parse(TIMESTAMP);
            Page<Event> page = new PageImpl<>(List.of(sampleEvent));

            when(eventRepository.findEventsWithFilters(
                    eq(TENANT_ID), eq("WEBSITE_VISIT"), eq(USERNAME), eq(DEVICE_ID),
                    eq(false), eq(false), eq(false), isNull(),
                    eq("General"), eq(start), eq(end), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<EventDto> result = eventService.getEventsWithFilters(
                    TENANT_ID, EventType.WEBSITE_VISIT, USERNAME, DEVICE_ID,
                    false, false, false, null,
                    "General", start, end, 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Happy Path — null eventType converted to null string for repository")
        void happyPath_nullEventType_passedAsNull() {
            // ARRANGE
            Page<Event> page = new PageImpl<>(Collections.emptyList());
            when(eventRepository.findEventsWithFilters(
                    eq(TENANT_ID), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<EventDto> result = eventService.getEventsWithFilters(
                    TENANT_ID, null, null, null,
                    null, null, null, null,
                    null, null, null, 0, 10);

            // ASSERT
            assertNotNull(result);
            verify(eventRepository).findEventsWithFilters(
                    eq(TENANT_ID), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), isNull(), any(Pageable.class));
        }
    }

    // =========================================================================
    // getEventCount
    // =========================================================================

    @Nested
    @DisplayName("getEventCount")
    class GetEventCount {

        @Test
        @DisplayName("Happy Path — returns correct event count for tenant")
        void happyPath_returnsCount() {
            // ARRANGE
            when(eventRepository.countByTenant_TenantID(TENANT_ID)).thenReturn(42L);

            // ACT
            long count = eventService.getEventCount(TENANT_ID);

            // ASSERT
            assertEquals(42L, count);
            verify(eventRepository).countByTenant_TenantID(TENANT_ID);
        }

        @Test
        @DisplayName("Happy Path — returns zero when no events exist")
        void happyPath_returnsZero() {
            // ARRANGE
            when(eventRepository.countByTenant_TenantID(TENANT_ID)).thenReturn(0L);

            // ACT
            long count = eventService.getEventCount(TENANT_ID);

            // ASSERT
            assertEquals(0L, count);
        }
    }

    // =========================================================================
    // getDistinctCategories / getDistinctUserNames
    // =========================================================================

    @Nested
    @DisplayName("getDistinctCategories and getDistinctUserNames")
    class GetDistincts {

        @Test
        @DisplayName("Happy Path — distinct categories returned")
        void happyPath_distinctCategories() {
            // ARRANGE
            when(eventRepository.findDistinctCategories(TENANT_ID))
                    .thenReturn(List.of("General", "Security", "FileOp"));

            // ACT
            List<String> result = eventService.getDistinctCategories(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(3, result.size());
            assertTrue(result.contains("General"));
        }

        @Test
        @DisplayName("Happy Path — empty list when no categories exist")
        void happyPath_noCategories_emptyList() {
            // ARRANGE
            when(eventRepository.findDistinctCategories(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<String> result = eventService.getDistinctCategories(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Happy Path — distinct usernames returned")
        void happyPath_distinctUserNames() {
            // ARRANGE
            when(eventRepository.findDistinctUserNames(TENANT_ID))
                    .thenReturn(List.of("alice", "bob", "jane.doe"));

            // ACT
            List<String> result = eventService.getDistinctUserNames(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertEquals(3, result.size());
            assertTrue(result.contains("jane.doe"));
        }

        @Test
        @DisplayName("Happy Path — empty list when no users exist")
        void happyPath_noUsers_emptyList() {
            // ARRANGE
            when(eventRepository.findDistinctUserNames(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            List<String> result = eventService.getDistinctUserNames(TENANT_ID);

            // ASSERT
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}