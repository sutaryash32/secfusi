package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.IncidentService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentController Tests")
class IncidentControllerTest {

    @Mock
    private IncidentService incidentService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private IncidentController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-1";
    private static final String USER_NAME = "testuser";
    private static final String INCIDENT_ID = "inc-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
        lenient().when(jwtUtl.getUserId(any())).thenReturn(USER_ID);
        lenient().when(jwtUtl.getUsername(any())).thenReturn(USER_NAME);
    }

    private IncidentDTO simpleIncidentDTO() {
        IncidentDTO dto = new IncidentDTO();
        dto.setIncidentId(INCIDENT_ID);
        dto.setIncidentNumber("INC-000001");
        dto.setTitle("Test Incident");
        dto.setStatus("OPEN");
        dto.setPriority("P3_MEDIUM");
        return dto;
    }

    // ==================== POST / ====================
    @Nested
    @DisplayName("POST /api/events/incidents")
    class CreateIncidentTests {
        @Test
        @DisplayName("Happy Path – returns 201 with created incident")
        void happyPath() throws Exception {
            CreateIncidentRequest req = new CreateIncidentRequest();
            req.setTitle("New Incident");
            req.setDescription("desc");
            req.setPriority("P3_MEDIUM");               // required field

            IncidentDTO dto = simpleIncidentDTO();
            when(incidentService.createIncident(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), any(CreateIncidentRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(post("/api/events/incidents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.results.incidentId", is(INCIDENT_ID)))
                    .andExpect(jsonPath("$.code", is("201")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            CreateIncidentRequest req = new CreateIncidentRequest();
            req.setTitle("Fail");
            req.setPriority("P3_MEDIUM");               // required field

            when(incidentService.createIncident(anyString(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(post("/api/events/incidents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }

    // ==================== GET / ====================
    @Nested
    @DisplayName("GET /api/events/incidents")
    class GetIncidentsTests {
        @Test
        @DisplayName("Happy Path – returns paginated incidents")
        void happyPath() throws Exception {
            List<IncidentDTO> content = new ArrayList<>(List.of(simpleIncidentDTO()));
            Page<IncidentDTO> page = new PageImpl<>(content, PageRequest.of(0, 20), content.size());
            when(incidentService.getIncidents(TENANT_ID, null, null, null, null, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/incidents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.content[0].incidentId", is(INCIDENT_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Happy Path – with filters")
        void happyPath_withFilters() throws Exception {
            Page<IncidentDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 10), 0);
            when(incidentService.getIncidents(TENANT_ID, "OPEN", "P1_CRITICAL", "MALWARE", null, 0, 10))
                    .thenReturn(page);

            mockMvc.perform(get("/api/events/incidents")
                            .param("status", "OPEN")
                            .param("priority", "P1_CRITICAL")
                            .param("category", "MALWARE")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(incidentService.getIncidents(anyString(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Error"));

            mockMvc.perform(get("/api/events/incidents"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Error")));
        }
    }

    // ==================== GET /stats ====================
    @Nested
    @DisplayName("GET /api/events/incidents/stats")
    class GetStatsTests {
        @Test
        @DisplayName("Happy Path – returns stats")
        void happyPath() throws Exception {
            IncidentStatsDTO stats = new IncidentStatsDTO();
            stats.setTotalIncidents(15L);
            when(incidentService.getStats(TENANT_ID)).thenReturn(stats);

            mockMvc.perform(get("/api/events/incidents/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalIncidents", is(15)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /dashboard ====================
    @Nested
    @DisplayName("GET /api/events/incidents/dashboard")
    class GetDashboardTests {
        @Test
        @DisplayName("Happy Path – returns dashboard")
        void happyPath() throws Exception {
            IncidentDashboardDTO dashboard = new IncidentDashboardDTO();
            dashboard.setStats(new IncidentStatsDTO());
            dashboard.getStats().setTotalIncidents(5L);
            when(incidentService.getDashboard(TENANT_ID)).thenReturn(dashboard);

            mockMvc.perform(get("/api/events/incidents/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /pending-events ====================
    @Nested
    @DisplayName("GET /api/events/incidents/pending-events")
    class GetPendingEventsTests {
        @Test
        @DisplayName("Happy Path – returns counts")
        void happyPath() throws Exception {
            Map<String, Long> counts = Map.of("totalPending", 10L);
            when(incidentService.getPendingEventCounts(TENANT_ID)).thenReturn(counts);

            mockMvc.perform(get("/api/events/incidents/pending-events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.totalPending", is(10)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /bulk-dismiss ====================
    @Nested
    @DisplayName("POST /api/events/incidents/bulk-dismiss")
    class BulkDismissTests {
        @Test
        @DisplayName("Happy Path – dismisses events")
        void happyPath() throws Exception {
            when(incidentService.bulkDismissOldEvents(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), any()))
                    .thenReturn(5);

            mockMvc.perform(post("/api/events/incidents/bulk-dismiss")
                            .param("cutoffDate", "2025-01-01T00:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.eventsDismissed", is(5)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(incidentService.bulkDismissOldEvents(anyString(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(post("/api/events/incidents/bulk-dismiss")
                            .param("cutoffDate", "2025-01-01T00:00:00"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }

    // ==================== POST /bulk-assign ====================
    @Nested
    @DisplayName("POST /api/events/incidents/bulk-assign")
    class BulkAssignTests {
        @Test
        @DisplayName("Happy Path – bulk assign completed")
        void happyPath() throws Exception {
            BulkAssignRequest req = new BulkAssignRequest();
            req.setIncidentIds(List.of(INCIDENT_ID));
            req.setAssignedTo("assignee-1");
            req.setAssignedToName("Assignee");

            BulkOperationResult result = new BulkOperationResult();
            result.setSucceeded(1);
            result.setTotal(1);
            when(incidentService.bulkAssign(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), any(BulkAssignRequest.class)))
                    .thenReturn(result);

            mockMvc.perform(post("/api/events/incidents/bulk-assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.succeeded", is(1)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /bulk-status ====================
    @Nested
    @DisplayName("POST /api/events/incidents/bulk-status")
    class BulkStatusTests {
        @Test
        @DisplayName("Happy Path – bulk status change completed")
        void happyPath() throws Exception {
            BulkStatusRequest req = new BulkStatusRequest();
            req.setIncidentIds(List.of(INCIDENT_ID));
            req.setNewStatus("CLOSED");

            BulkOperationResult result = new BulkOperationResult();
            result.setSucceeded(1);
            when(incidentService.bulkStatusChange(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), any(BulkStatusRequest.class)))
                    .thenReturn(result);

            mockMvc.perform(post("/api/events/incidents/bulk-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.succeeded", is(1)));
        }
    }

    // ==================== POST /bulk-close ====================
    @Nested
    @DisplayName("POST /api/events/incidents/bulk-close")
    class BulkCloseTests {
        @Test
        @DisplayName("Happy Path – bulk close completed")
        void happyPath() throws Exception {
            BulkCloseRequest req = new BulkCloseRequest();
            req.setIncidentIds(List.of(INCIDENT_ID));

            BulkOperationResult result = new BulkOperationResult();
            result.setSucceeded(1);
            when(incidentService.bulkClose(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), any(BulkCloseRequest.class)))
                    .thenReturn(result);

            mockMvc.perform(post("/api/events/incidents/bulk-close")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.succeeded", is(1)));
        }
    }

    // ==================== POST /assignees ====================
    @Nested
    @DisplayName("POST /api/events/incidents/assignees")
    class AddAssigneeConfigTests {
        @Test
        @DisplayName("Happy Path – creates assignee config")
        void happyPath() throws Exception {
            CreateIncidentAssigneeRequest req = new CreateIncidentAssigneeRequest();
            req.setUserId("assignee-1");
            req.setUserName("Assignee");              // required field

            IncidentAssigneeDTO dto = new IncidentAssigneeDTO();
            dto.setUserId("assignee-1");
            when(incidentService.addAssigneeConfig(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), any(CreateIncidentAssigneeRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(post("/api/events/incidents/assignees")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.results.userId", is("assignee-1")))
                    .andExpect(jsonPath("$.code", is("201")));
        }

        @Test
        @DisplayName("Sad Path – duplicate returns 409")
        void sadPath_duplicate() throws Exception {
            CreateIncidentAssigneeRequest req = new CreateIncidentAssigneeRequest();
            req.setUserId("assignee-1");
            req.setUserName("Assignee");              // required

            when(incidentService.addAssigneeConfig(anyString(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Config already exists"));

            mockMvc.perform(post("/api/events/incidents/assignees")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", containsString("already exists")));
        }
    }

    // ==================== POST /assignees/bulk-init ====================
    @Nested
    @DisplayName("POST /api/events/incidents/assignees/bulk-init")
    class BulkInitAssigneesTests {
        @Test
        @DisplayName("Happy Path – bulk init")
        void happyPath() throws Exception {
            CreateIncidentAssigneeRequest req = new CreateIncidentAssigneeRequest();
            req.setUserId("assignee-1");
            req.setUserName("Assignee");              // required
            List<CreateIncidentAssigneeRequest> reqList = List.of(req);
            when(incidentService.bulkInitAssigneeConfigs(eq(TENANT_ID), anyList()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(post("/api/events/incidents/assignees/bulk-init")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqList)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code", is("201")));
        }
    }

    // ==================== GET /assignees ====================
    @Nested
    @DisplayName("GET /api/events/incidents/assignees")
    class ListAssigneesTests {
        @Test
        @DisplayName("Happy Path – returns list")
        void happyPath() throws Exception {
            when(incidentService.listAssigneeConfigs(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/events/incidents/assignees"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== PUT /assignees/{assigneeId} ====================
    @Nested
    @DisplayName("PUT /api/events/incidents/assignees/{assigneeId}")
    class UpdateAssigneeConfigTests {
        @Test
        @DisplayName("Happy Path – updates config")
        void happyPath() throws Exception {
            Map<String, Object> body = Map.of("isActive", false, "assignmentOrder", 1);
            IncidentAssigneeDTO dto = new IncidentAssigneeDTO();
            dto.setUserId("assignee-1");
            when(incidentService.updateAssigneeConfig(eq(TENANT_ID), eq("ass-1"), eq(false), eq(1), isNull()))
                    .thenReturn(dto);

            mockMvc.perform(put("/api/events/incidents/assignees/ass-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.userId", is("assignee-1")));
        }
    }

    // ==================== DELETE /assignees/{assigneeId} ====================
    @Nested
    @DisplayName("DELETE /api/events/incidents/assignees/{assigneeId}")
    class RemoveAssigneeConfigTests {
        @Test
        @DisplayName("Happy Path – removes config")
        void happyPath() throws Exception {
            doNothing().when(incidentService).removeAssigneeConfig(TENANT_ID, "ass-1");

            mockMvc.perform(delete("/api/events/incidents/assignees/ass-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /auto-create ====================
    @Nested
    @DisplayName("POST /api/events/incidents/auto-create")
    class AutoCreateTests {
        @Test
        @DisplayName("Happy Path – auto creates incidents")
        void happyPath() throws Exception {
            when(incidentService.autoCreateIncidents(TENANT_ID, USER_ID, USER_NAME))
                    .thenReturn(List.of(simpleIncidentDTO()));

            mockMvc.perform(post("/api/events/incidents/auto-create"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].incidentId", is(INCIDENT_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /{parentId}/merge ====================
    @Nested
    @DisplayName("POST /api/events/incidents/{parentId}/merge")
    class MergeIncidentsTests {
        @Test
        @DisplayName("Happy Path – merges incidents")
        void happyPath() throws Exception {
            MergeIncidentsRequest req = new MergeIncidentsRequest();
            req.setChildIncidentIds(List.of("child-1"));

            IncidentDTO merged = simpleIncidentDTO();
            when(incidentService.mergeIncidents(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), eq(INCIDENT_ID), any(MergeIncidentsRequest.class)))
                    .thenReturn(merged);

            mockMvc.perform(post("/api/events/incidents/{parentId}/merge", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.incidentId", is(INCIDENT_ID)));
        }

        @Test
        @DisplayName("Sad Path – IllegalStateException returns 400")
        void sadPath_illegalState() throws Exception {
            MergeIncidentsRequest req = new MergeIncidentsRequest();
            req.setChildIncidentIds(List.of("child-1"));

            when(incidentService.mergeIncidents(anyString(), anyString(), anyString(), anyString(), any()))
                    .thenThrow(new IllegalStateException("Cannot merge"));

            mockMvc.perform(post("/api/events/incidents/{parentId}/merge", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("Cannot merge")));
        }
    }

    // ==================== GET /{id} (detail) ====================
    @Nested
    @DisplayName("GET /api/events/incidents/{id}")
    class GetIncidentDetailTests {
        @Test
        @DisplayName("Happy Path – returns incident detail")
        void happyPath() throws Exception {
            IncidentDetailDTO detail = new IncidentDetailDTO();
            detail.setIncidentId(INCIDENT_ID);
            detail.setTitle("Test");
            when(incidentService.getIncidentDetail(TENANT_ID, INCIDENT_ID)).thenReturn(detail);

            mockMvc.perform(get("/api/events/incidents/{id}", INCIDENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.incidentId", is(INCIDENT_ID)));
        }

        @Test
        @DisplayName("Sad Path – not found returns 500")
        void sadPath_notFound() throws Exception {
            when(incidentService.getIncidentDetail(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Not found"));

            mockMvc.perform(get("/api/events/incidents/{id}", "bad"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Not found")));
        }
    }

    // ==================== PUT /{id} (update) ====================
    @Nested
    @DisplayName("PUT /api/events/incidents/{id}")
    class UpdateIncidentTests {
        @Test
        @DisplayName("Happy Path – updates incident")
        void happyPath() throws Exception {
            UpdateIncidentRequest req = new UpdateIncidentRequest();
            req.setTitle("Updated Title");

            IncidentDTO updated = simpleIncidentDTO();
            updated.setTitle("Updated Title");
            when(incidentService.updateIncident(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), eq(INCIDENT_ID), any(UpdateIncidentRequest.class)))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/events/incidents/{id}", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.title", is("Updated Title")));
        }
    }

    // ==================== PATCH /{id}/status ====================
    @Nested
    @DisplayName("PATCH /api/events/incidents/{id}/status")
    class ChangeStatusTests {
        @Test
        @DisplayName("Happy Path – changes status")
        void happyPath() throws Exception {
            IncidentDTO dto = simpleIncidentDTO();
            dto.setStatus("INVESTIGATING");
            when(incidentService.changeStatus(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, "INVESTIGATING")).thenReturn(dto);

            mockMvc.perform(patch("/api/events/incidents/{id}/status", INCIDENT_ID)
                            .param("status", "INVESTIGATING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.status", is("INVESTIGATING")));
        }

        @Test
        @DisplayName("Sad Path – invalid transition returns 400")
        void sadPath_invalidTransition() throws Exception {
            when(incidentService.changeStatus(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalArgumentException("Invalid transition"));

            mockMvc.perform(patch("/api/events/incidents/{id}/status", INCIDENT_ID)
                            .param("status", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("Invalid transition")));
        }
    }

    // ==================== PATCH /{id}/resolve ====================
    @Nested
    @DisplayName("PATCH /api/events/incidents/{id}/resolve")
    class ResolveIncidentTests {
        @Test
        @DisplayName("Happy Path – resolves incident")
        void happyPath() throws Exception {
            ResolveIncidentRequest req = new ResolveIncidentRequest();
            req.setResolutionNotes("Done");

            IncidentDTO resolved = simpleIncidentDTO();
            resolved.setStatus("RESOLVED");
            when(incidentService.resolveIncident(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), eq(INCIDENT_ID), any(ResolveIncidentRequest.class)))
                    .thenReturn(resolved);

            mockMvc.perform(patch("/api/events/incidents/{id}/resolve", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.status", is("RESOLVED")));
        }
    }

    // ==================== PATCH /{id}/assign ====================
    @Nested
    @DisplayName("PATCH /api/events/incidents/{id}/assign")
    class AssignIncidentTests {
        @Test
        @DisplayName("Happy Path – assigns incident")
        void happyPath() throws Exception {
            AssignIncidentRequest req = new AssignIncidentRequest("assignee-1", "Assignee");

            IncidentDTO assigned = simpleIncidentDTO();
            assigned.setAssignedTo("assignee-1");
            when(incidentService.assignIncident(eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), eq(INCIDENT_ID), any(AssignIncidentRequest.class)))
                    .thenReturn(assigned);

            mockMvc.perform(patch("/api/events/incidents/{id}/assign", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.assignedTo", is("assignee-1")));
        }
    }

    // ==================== PATCH /{id}/priority ====================
    @Nested
    @DisplayName("PATCH /api/events/incidents/{id}/priority")
    class ChangePriorityTests {
        @Test
        @DisplayName("Happy Path – changes priority")
        void happyPath() throws Exception {
            IncidentDTO dto = simpleIncidentDTO();
            dto.setPriority("P1_CRITICAL");
            when(incidentService.changePriority(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, "P1_CRITICAL")).thenReturn(dto);

            mockMvc.perform(patch("/api/events/incidents/{id}/priority", INCIDENT_ID)
                            .param("priority", "P1_CRITICAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.priority", is("P1_CRITICAL")));
        }

        @Test
        @DisplayName("Sad Path – invalid priority returns 400")
        void sadPath_invalidPriority() throws Exception {
            when(incidentService.changePriority(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalArgumentException("Invalid priority"));

            mockMvc.perform(patch("/api/events/incidents/{id}/priority", INCIDENT_ID)
                            .param("priority", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("Invalid priority")));
        }
    }

    // ==================== POST /{id}/events (link) ====================
    @Nested
    @DisplayName("POST /api/events/incidents/{id}/events")
    class LinkEventsTests {
        @Test
        @DisplayName("Happy Path – links events")
        void happyPath() throws Exception {
            List<String> eventIds = List.of("ev-1", "ev-2");
            IncidentDTO dto = simpleIncidentDTO();
            dto.setEventCount(2);
            when(incidentService.linkEvents(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, eventIds)).thenReturn(dto);

            mockMvc.perform(post("/api/events/incidents/{id}/events", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(eventIds)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.eventCount", is(2)));
        }
    }

    // ==================== DELETE /{id}/events/{eventId} ====================
    @Nested
    @DisplayName("DELETE /api/events/incidents/{id}/events/{eventId}")
    class UnlinkEventTests {
        @Test
        @DisplayName("Happy Path – unlinks event")
        void happyPath() throws Exception {
            doNothing().when(incidentService).unlinkEvent(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, "ev-1");

            mockMvc.perform(delete("/api/events/incidents/{id}/events/{eventId}", INCIDENT_ID, "ev-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /{id}/events (linked) ====================
    @Nested
    @DisplayName("GET /api/events/incidents/{id}/events")
    class GetLinkedEventsTests {
        @Test
        @DisplayName("Happy Path – returns linked events")
        void happyPath() throws Exception {
            when(incidentService.getLinkedEvents(TENANT_ID, INCIDENT_ID))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/events/incidents/{id}/events", INCIDENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /{id}/timeline ====================
    @Nested
    @DisplayName("GET /api/events/incidents/{id}/timeline")
    class GetTimelineTests {
        @Test
        @DisplayName("Happy Path – returns paginated timeline")
        void happyPath() throws Exception {
            Page<IncidentActivityDTO> page = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 20), 0);
            when(incidentService.getTimeline(TENANT_ID, INCIDENT_ID, 0, 20)).thenReturn(page);

            mockMvc.perform(get("/api/events/incidents/{id}/timeline", INCIDENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /{id}/comments ====================
    @Nested
    @DisplayName("POST /api/events/incidents/{id}/comments")
    class AddCommentTests {
        @Test
        @DisplayName("Happy Path – adds comment")
        void happyPath() throws Exception {
            Map<String, String> body = Map.of("comment", "Nice work");
            IncidentActivityDTO activity = new IncidentActivityDTO();
            activity.setActivityId("act-1");
            when(incidentService.addComment(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, "Nice work"))
                    .thenReturn(activity);

            mockMvc.perform(post("/api/events/incidents/{id}/comments", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.activityId", is("act-1")));
        }

        @Test
        @DisplayName("Sad Path – missing comment returns 400")
        void sadPath_missingComment() throws Exception {
            Map<String, String> body = Map.of("comment", "");

            mockMvc.perform(post("/api/events/incidents/{id}/comments", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("Comment is required")));
        }
    }
}