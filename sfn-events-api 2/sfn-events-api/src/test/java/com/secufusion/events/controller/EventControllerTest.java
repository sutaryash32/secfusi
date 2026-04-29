package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.kafka.EventKafkaProducer;
import com.secufusion.events.service.ActivitySummaryService;
import com.secufusion.events.service.EventService;
import com.secufusion.events.util.JwtUtl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventController Tests")
class EventControllerTest {

    @Mock private EventService eventService;
    @Mock private ActivitySummaryService activitySummaryService;
    @Mock private JwtUtl jwtUtl;
    @Mock private EventKafkaProducer eventKafkaProducer;

    @InjectMocks
    private EventController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_NAME = "testuser";

    @BeforeEach
    void setUp() {
        // Inject field-level mocks (not constructor args)
        ReflectionTestUtils.setField(controller, "jwtUtl", jwtUtl);
        ReflectionTestUtils.setField(controller, "eventKafkaProducer", eventKafkaProducer);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
        lenient().when(jwtUtl.getPreferredUsernameFromRequest(any())).thenReturn(USER_NAME);
    }

    private EventRequestDto createEventRequestDto() {
        EventRequestDto dto = new EventRequestDto();
        EventDto event = new EventDto();
        event.setUrl("https://example.com");
        event.setTimeStamp(LocalDateTime.now().toString());
        dto.setEvents(List.of(event));
        return dto;
    }

    // ==================== POST /events ====================
    @Nested
    @DisplayName("POST /api/events")
    class AddEventsTests {

        @Test
        @DisplayName("Happy Path – saves events successfully")
        void happyPath() throws Exception {
            EventRequestDto requestDto = createEventRequestDto();
            doNothing().when(eventService).saveAllEvents(any(), anyList());

            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Sad Path – empty payload returns 400")
        void sadPath_emptyPayload() throws Exception {
            EventRequestDto emptyDto = new EventRequestDto();

            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Sad Path – service error returns 500")
        void sadPath_serviceException() throws Exception {
            EventRequestDto requestDto = createEventRequestDto();
            doThrow(new RuntimeException("DB error")).when(eventService).saveAllEvents(any(), anyList());

            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ==================== POST /events/kafka ====================
    @Nested
    @DisplayName("POST /api/events/kafka")
    class AddEventsToKafkaTests {

        @Test
        @DisplayName("Happy Path – events accepted (202)")
        void happyPath() throws Exception {
            EventRequestDto requestDto = createEventRequestDto();
            doNothing().when(eventKafkaProducer).send(any());

            mockMvc.perform(post("/api/events/kafka")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("Sad Path – empty payload returns 400")
        void sadPath_emptyPayload() throws Exception {
            EventRequestDto emptyDto = new EventRequestDto();

            mockMvc.perform(post("/api/events/kafka")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyDto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== POST /events/kafka/sync ====================
    @Nested
    @DisplayName("POST /api/events/kafka/sync")
    class SyncTenantTests {

        @Test
        @DisplayName("Happy Path – tenant flushed successfully (202)")
        void happyPath() throws Exception {
            doNothing().when(eventService).flushTenant(TENANT_ID);

            mockMvc.perform(post("/api/events/kafka/sync"))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("Sad Path – failure returns 500")
        void sadPath_error() throws Exception {
            doThrow(new RuntimeException("Flush failed")).when(eventService).flushTenant(TENANT_ID);

            mockMvc.perform(post("/api/events/kafka/sync"))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ==================== GET /events ====================
    @Nested
    @DisplayName("GET /api/events")
    class GetUserEventsTests {

        @Test
        @DisplayName("Happy Path – returns list of user events")
        void happyPath() throws Exception {
            UserEventsResponseDto userEvent = new UserEventsResponseDto();
            userEvent.setUserName(USER_NAME);
            when(eventService.getUserEvents(TENANT_ID)).thenReturn(List.of(userEvent));

            mockMvc.perform(get("/api/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].userName", is(USER_NAME)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /events/summary ====================
    @Nested
    @DisplayName("GET /api/events/summary")
    class GetTenantActivitySummaryTests {

        @Test
        @DisplayName("Happy Path – returns summary")
        void happyPath() throws Exception {
            TenantActivitySummaryDTO summary = new TenantActivitySummaryDTO();
            summary.setTotalEventsToday(125L);
            when(activitySummaryService.getTenantActivitySummary(TENANT_ID)).thenReturn(summary);

            mockMvc.perform(get("/api/events/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalEventsToday", is(125)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – error returns 500 with message in code")
        void sadPath_error() throws Exception {
            when(activitySummaryService.getTenantActivitySummary(TENANT_ID))
                    .thenThrow(new RuntimeException("Summary failed"));

            mockMvc.perform(get("/api/events/summary"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Summary failed")));
        }
    }

    // ==================== GET /events/device/{deviceId} ====================
    @Nested
    @DisplayName("GET /api/events/device/{deviceId}")
    class GetDeviceEventsTests {

        @Test
        @DisplayName("Happy Path – paginated events")
        void happyPath() throws Exception {
            List<EventDto> content = new ArrayList<>(List.of(new EventDto()));
            Page<EventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(eventService.getEventsByDevice("dev1", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/device/dev1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content.size()", is(1)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – error returns 500")
        void sadPath_error() throws Exception {
            when(eventService.getEventsByDevice("dev1", 0, 20))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/device/dev1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== GET /events/device/{deviceId}/range ====================
    @Nested
    @DisplayName("GET /api/events/device/{deviceId}/range")
    class GetDeviceEventsByRangeTests {

        @Test
        @DisplayName("Happy Path – returns list of events")
        void happyPath() throws Exception {
            List<EventDto> events = List.of(new EventDto());
            // use any() for LocalDateTime parameters to avoid type inference issues
            when(eventService.getEventsByDeviceAndTimeRange(eq("dev1"), any(), any()))
                    .thenReturn(events);

            mockMvc.perform(get("/api/events/device/dev1/range")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.size()", is(1)));
        }

        @Test
        @DisplayName("Sad Path – error returns 500")
        void sadPath_error() throws Exception {
            when(eventService.getEventsByDeviceAndTimeRange(anyString(), any(), any()))
                    .thenThrow(new RuntimeException("Range error"));

            mockMvc.perform(get("/api/events/device/dev1/range")
                            .param("start", "2025-01-01T00:00:00")
                            .param("end", "2025-01-02T00:00:00"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Range error")));
        }
    }

    // ==================== GET /events/user/{userName} ====================
    @Nested
    @DisplayName("GET /api/events/user/{userName}")
    class GetEventsByUserTests {

        @Test
        @DisplayName("Happy Path – paginated user events")
        void happyPath() throws Exception {
            List<EventDto> content = new ArrayList<>(List.of(new EventDto()));
            Page<EventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(eventService.getEventsByUser(TENANT_ID, USER_NAME, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/user/" + USER_NAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content.size()", is(1)));
        }
    }

    // ==================== GET /events/all ====================
    @Nested
    @DisplayName("GET /api/events/all")
    class GetAllEventsTests {

        @Test
        @DisplayName("Happy Path – paginated results")
        void happyPath() throws Exception {
            List<EventDto> content = new ArrayList<>(List.of(new EventDto()));
            Page<EventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(eventService.getAllEvents(eq(TENANT_ID), any(), any(), eq(0), eq(20))).thenReturn(page);

            mockMvc.perform(get("/api/events/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content.size()", is(1)));
        }
    }

    // ==================== GET /events/search ====================
    @Nested
    @DisplayName("GET /api/events/search")
    class SearchEventsTests {

        @Test
        @DisplayName("Happy Path – search with term")
        void happyPath() throws Exception {
            List<EventDto> content = new ArrayList<>(List.of(new EventDto()));
            Page<EventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(eventService.searchEvents(TENANT_ID, "test", 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/search")
                            .param("q", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content.size()", is(1)));
        }

        @Test
        @DisplayName("Sad Path – missing q returns 400 with message in code")
        void sadPath_missingTerm() throws Exception {
            mockMvc.perform(get("/api/events/search")
                            .param("q", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("Search term 'q' is required")));
        }
    }

    // ==================== GET /events/filter ====================
    @Nested
    @DisplayName("GET /api/events/filter")
    class FilterEventsTests {

        @Test
        @DisplayName("Happy Path – minimal filter")
        void happyPath() throws Exception {
            List<EventDto> content = new ArrayList<>(List.of(new EventDto()));
            Page<EventDto> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(eventService.getEventsWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(null),
                    eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/events/filter"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content.size()", is(1)));
        }

        @Test
        @DisplayName("Sad Path – error returns 500")
        void sadPath_error() throws Exception {
            when(eventService.getEventsWithFilters(any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Filter error"));

            mockMvc.perform(get("/api/events/filter"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Filter error")));
        }
    }

    // ==================== GET /events/count ====================
    @Nested
    @DisplayName("GET /api/events/count")
    class GetEventCountTests {

        @Test
        @DisplayName("Happy Path – returns count")
        void happyPath() throws Exception {
            when(eventService.getEventCount(TENANT_ID)).thenReturn(42L);

            mockMvc.perform(get("/api/events/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results", is(42)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /events/categories ====================
    @Nested
    @DisplayName("GET /api/events/categories")
    class GetCategoriesTests {

        @Test
        @DisplayName("Happy Path – returns list")
        void happyPath() throws Exception {
            when(eventService.getDistinctCategories(TENANT_ID)).thenReturn(List.of("cat1", "cat2"));

            mockMvc.perform(get("/api/events/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.size()", is(2)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /events/users ====================
    @Nested
    @DisplayName("GET /api/events/users")
    class GetEventUsersTests {

        @Test
        @DisplayName("Happy Path – returns list")
        void happyPath() throws Exception {
            when(eventService.getDistinctUserNames(TENANT_ID)).thenReturn(List.of("user1", "user2"));

            mockMvc.perform(get("/api/events/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.size()", is(2)));
        }
    }
}