package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.*;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.PlaybookService;
import com.secufusion.events.util.JwtUtl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaybookController Tests")
class PlaybookControllerTest {

    @Mock
    private PlaybookService playbookService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private PlaybookController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-1";
    private static final String USER_NAME = "testuser";
    private static final String TEMPLATE_ID = "tmpl-1";
    private static final String INCIDENT_ID = "inc-1";
    private static final String PLAYBOOK_ID = "pb-1";
    private static final String STEP_ID = "step-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
        lenient().when(jwtUtl.getUserId(any())).thenReturn(USER_ID);
        lenient().when(jwtUtl.getUsername(any())).thenReturn(USER_NAME);
    }

    // ==================== POST /api/events/playbooks ====================
    @Nested
    @DisplayName("POST /api/events/playbooks")
    class CreateTemplateTests {
        @Test
        @DisplayName("Happy Path – returns 201 with created template")
        void happyPath() throws Exception {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("Test Template");
            req.setSteps(List.of(Map.of("title", "Step 1")));  // required

            PlaybookTemplateDTO dto = new PlaybookTemplateDTO();
            dto.setTemplateId(TEMPLATE_ID);
            dto.setName("Test Template");
            when(playbookService.createTemplate(eq(TENANT_ID), any(CreatePlaybookTemplateRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(post("/api/events/playbooks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.results.templateId", is(TEMPLATE_ID)))
                    .andExpect(jsonPath("$.code", is("201")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("Fail");
            req.setSteps(List.of(Map.of("title", "Step")));   // required, otherwise validation fails with 400

            when(playbookService.createTemplate(anyString(), any()))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(post("/api/events/playbooks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB error")));
        }
    }

    // ==================== GET /api/events/playbooks ====================
    @Nested
    @DisplayName("GET /api/events/playbooks")
    class ListTemplatesTests {
        @Test
        @DisplayName("Happy Path – returns list of templates")
        void happyPath() throws Exception {
            PlaybookTemplateDTO dto = new PlaybookTemplateDTO();
            dto.setTemplateId(TEMPLATE_ID);
            when(playbookService.listTemplates(TENANT_ID, null)).thenReturn(List.of(dto));

            mockMvc.perform(get("/api/events/playbooks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].templateId", is(TEMPLATE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Happy Path – with category filter")
        void happyPath_withCategory() throws Exception {
            when(playbookService.listTemplates(TENANT_ID, "MALWARE")).thenReturn(List.of());

            mockMvc.perform(get("/api/events/playbooks")
                            .param("category", "MALWARE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== GET /api/events/playbooks/{templateId} ====================
    @Nested
    @DisplayName("GET /api/events/playbooks/{templateId}")
    class GetTemplateTests {
        @Test
        @DisplayName("Happy Path – returns template detail")
        void happyPath() throws Exception {
            PlaybookTemplateDTO dto = new PlaybookTemplateDTO();
            dto.setTemplateId(TEMPLATE_ID);
            when(playbookService.getTemplate(TENANT_ID, TEMPLATE_ID)).thenReturn(dto);

            mockMvc.perform(get("/api/events/playbooks/{templateId}", TEMPLATE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.templateId", is(TEMPLATE_ID)));
        }
    }

    // ==================== PUT /api/events/playbooks/{templateId} ====================
    @Nested
    @DisplayName("PUT /api/events/playbooks/{templateId}")
    class UpdateTemplateTests {
        @Test
        @DisplayName("Happy Path – updates template")
        void happyPath() throws Exception {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("Updated");
            req.setSteps(List.of(Map.of("title", "Updated Step"))); // required non-empty list

            PlaybookTemplateDTO dto = new PlaybookTemplateDTO();
            dto.setTemplateId(TEMPLATE_ID);
            dto.setName("Updated");
            when(playbookService.updateTemplate(eq(TENANT_ID), eq(TEMPLATE_ID), any(CreatePlaybookTemplateRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(put("/api/events/playbooks/{templateId}", TEMPLATE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.name", is("Updated")));
        }
    }

    // ==================== DELETE /api/events/playbooks/{templateId} ====================
    @Nested
    @DisplayName("DELETE /api/events/playbooks/{templateId}")
    class DeactivateTemplateTests {
        @Test
        @DisplayName("Happy Path – deactivates template")
        void happyPath() throws Exception {
            doNothing().when(playbookService).deactivateTemplate(TENANT_ID, TEMPLATE_ID);

            mockMvc.perform(delete("/api/events/playbooks/{templateId}", TEMPLATE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is("200")));
        }
    }

    // ==================== POST /api/events/incidents/{incidentId}/playbooks ====================
    @Nested
    @DisplayName("POST /api/events/incidents/{incidentId}/playbooks")
    class AttachPlaybookTests {
        @Test
        @DisplayName("Happy Path – attaches playbook and returns 201")
        void happyPath() throws Exception {
            AttachPlaybookRequest req = new AttachPlaybookRequest();
            req.setTemplateId(TEMPLATE_ID);

            IncidentPlaybookDTO dto = new IncidentPlaybookDTO();
            dto.setPlaybookId(PLAYBOOK_ID);
            when(playbookService.attachPlaybook(
                    eq(TENANT_ID), eq(USER_ID), eq(USER_NAME), eq(INCIDENT_ID), any(AttachPlaybookRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(post("/api/events/incidents/{incidentId}/playbooks", INCIDENT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.results.playbookId", is(PLAYBOOK_ID)))
                    .andExpect(jsonPath("$.code", is("201")));
        }
    }

    // ==================== GET /api/events/incidents/{incidentId}/playbooks ====================
    @Nested
    @DisplayName("GET /api/events/incidents/{incidentId}/playbooks")
    class GetIncidentPlaybooksTests {
        @Test
        @DisplayName("Happy Path – returns list of playbooks")
        void happyPath() throws Exception {
            IncidentPlaybookDTO dto = new IncidentPlaybookDTO();
            dto.setPlaybookId(PLAYBOOK_ID);
            when(playbookService.getIncidentPlaybooks(TENANT_ID, INCIDENT_ID)).thenReturn(List.of(dto));

            mockMvc.perform(get("/api/events/incidents/{incidentId}/playbooks", INCIDENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].playbookId", is(PLAYBOOK_ID)));
        }
    }

    // ==================== PATCH …/complete ====================
    @Nested
    @DisplayName("PATCH /api/events/incidents/{incidentId}/playbooks/{playbookId}/steps/{stepId}/complete")
    class CompleteStepTests {
        @Test
        @DisplayName("Happy Path – completes step")
        void happyPath() throws Exception {
            CompleteStepRequest req = new CompleteStepRequest();
            req.setNotes("Done");

            IncidentPlaybookStepDTO dto = new IncidentPlaybookStepDTO();
            dto.setStepId(STEP_ID);
            when(playbookService.completeStep(
                    eq(TENANT_ID), eq(USER_ID), eq(USER_NAME),
                    eq(INCIDENT_ID), eq(PLAYBOOK_ID), eq(STEP_ID), any(CompleteStepRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(patch("/api/events/incidents/{incidentId}/playbooks/{playbookId}/steps/{stepId}/complete",
                            INCIDENT_ID, PLAYBOOK_ID, STEP_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.stepId", is(STEP_ID)));
        }
    }

    // ==================== PATCH …/uncomplete ====================
    @Nested
    @DisplayName("PATCH /api/events/incidents/{incidentId}/playbooks/{playbookId}/steps/{stepId}/uncomplete")
    class UncompleteStepTests {
        @Test
        @DisplayName("Happy Path – uncompletes step")
        void happyPath() throws Exception {
            IncidentPlaybookStepDTO dto = new IncidentPlaybookStepDTO();
            dto.setStepId(STEP_ID);
            when(playbookService.uncompleteStep(
                    eq(TENANT_ID), eq(USER_ID), eq(USER_NAME),
                    eq(INCIDENT_ID), eq(PLAYBOOK_ID), eq(STEP_ID)))
                    .thenReturn(dto);

            mockMvc.perform(patch("/api/events/incidents/{incidentId}/playbooks/{playbookId}/steps/{stepId}/uncomplete",
                            INCIDENT_ID, PLAYBOOK_ID, STEP_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.stepId", is(STEP_ID)));
        }
    }
}