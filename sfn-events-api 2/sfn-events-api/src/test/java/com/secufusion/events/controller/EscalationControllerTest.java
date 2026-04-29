package com.secufusion.events.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.dto.CreateEscalationRuleRequest;
import com.secufusion.events.dto.EscalationRuleDTO;
import com.secufusion.events.dto.ResponseDto;
import com.secufusion.events.entity.Tenant;
import com.secufusion.events.service.EscalationService;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EscalationController Tests")
class EscalationControllerTest {

    @Mock
    private EscalationService escalationService;

    @Mock
    private JwtUtl jwtUtl;

    @InjectMocks
    private EscalationController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TENANT_ID = "tenant-1";
    private static final String RULE_ID = "rule-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Tenant tenant = new Tenant();
        tenant.setTenantID(TENANT_ID);
        lenient().when(jwtUtl.getTenantFromRequest(any())).thenReturn(tenant);
    }

    // Helper to create a dummy DTO
    private EscalationRuleDTO sampleRuleDTO() {
        EscalationRuleDTO dto = new EscalationRuleDTO();
        dto.setRuleId(RULE_ID);
        dto.setName("Test Rule");
        dto.setEscalateToPriority("P1_CRITICAL");
        dto.setIsActive(true);
        return dto;
    }

    // ==================== POST / ====================
    @Nested
    @DisplayName("POST /api/events/escalation-rules")
    class CreateRuleTests {

        @Test
        @DisplayName("Happy Path – returns 201 with created rule")
        void happyPath() throws Exception {
            CreateEscalationRuleRequest req = new CreateEscalationRuleRequest();
            req.setName("Test Rule");
            req.setEscalateToPriority("P1_CRITICAL");

            EscalationRuleDTO dto = sampleRuleDTO();
            when(escalationService.createRule(eq(TENANT_ID), any(CreateEscalationRuleRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(post("/api/events/escalation-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.results.ruleId", is(RULE_ID)))
                    .andExpect(jsonPath("$.code", is("201")));
        }

        @Test
        @DisplayName("Sad Path – duplicate name returns 409")
        void sadPath_duplicate() throws Exception {
            CreateEscalationRuleRequest req = new CreateEscalationRuleRequest();
            req.setName("Duplicate");

            when(escalationService.createRule(eq(TENANT_ID), any(CreateEscalationRuleRequest.class)))
                    .thenThrow(new RuntimeException("Rule with name 'Duplicate' already exists"));

            mockMvc.perform(post("/api/events/escalation-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", containsString("already exists")));
        }

        @Test
        @DisplayName("Sad Path – other exception returns 500")
        void sadPath_otherException() throws Exception {
            CreateEscalationRuleRequest req = new CreateEscalationRuleRequest();
            req.setName("Test");

            when(escalationService.createRule(eq(TENANT_ID), any(CreateEscalationRuleRequest.class)))
                    .thenThrow(new RuntimeException("DB failure"));

            mockMvc.perform(post("/api/events/escalation-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("DB failure")));
        }
    }

    // ==================== GET / ====================
    @Nested
    @DisplayName("GET /api/events/escalation-rules")
    class ListRulesTests {

        @Test
        @DisplayName("Happy Path – returns list of rules")
        void happyPath() throws Exception {
            EscalationRuleDTO dto = sampleRuleDTO();
            when(escalationService.listRules(TENANT_ID)).thenReturn(java.util.List.of(dto));

            mockMvc.perform(get("/api/events/escalation-rules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results[0].ruleId", is(RULE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            when(escalationService.listRules(TENANT_ID))
                    .thenThrow(new RuntimeException("Error"));

            mockMvc.perform(get("/api/events/escalation-rules"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Error")));
        }
    }

    // ==================== PUT /{ruleId} ====================
    @Nested
    @DisplayName("PUT /api/events/escalation-rules/{ruleId}")
    class UpdateRuleTests {

        @Test
        @DisplayName("Happy Path – returns updated rule")
        void happyPath() throws Exception {
            CreateEscalationRuleRequest req = new CreateEscalationRuleRequest();
            req.setName("Updated Rule");

            EscalationRuleDTO dto = sampleRuleDTO();
            when(escalationService.updateRule(eq(TENANT_ID), eq(RULE_ID), any(CreateEscalationRuleRequest.class)))
                    .thenReturn(dto);

            mockMvc.perform(put("/api/events/escalation-rules/{ruleId}", RULE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.ruleId", is(RULE_ID)))
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – rule not found → 500 (since exception is caught)")
        void sadPath_notFound() throws Exception {
            CreateEscalationRuleRequest req = new CreateEscalationRuleRequest();
            req.setName("NonExistent");

            when(escalationService.updateRule(eq(TENANT_ID), eq("bad-rule"), any(CreateEscalationRuleRequest.class)))
                    .thenThrow(new RuntimeException("Rule not found"));

            mockMvc.perform(put("/api/events/escalation-rules/{ruleId}", "bad-rule")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Rule not found")));
        }
    }

    // ==================== DELETE /{ruleId} ====================
    @Nested
    @DisplayName("DELETE /api/events/escalation-rules/{ruleId}")
    class DeactivateRuleTests {

        @Test
        @DisplayName("Happy Path – returns success message")
        void happyPath() throws Exception {
            doNothing().when(escalationService).deactivateRule(TENANT_ID, RULE_ID);

            mockMvc.perform(delete("/api/events/escalation-rules/{ruleId}", RULE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", is("Escalation rule deactivated successfully")))  // changed
                    .andExpect(jsonPath("$.code", is("200")));
        }

        @Test
        @DisplayName("Sad Path – service exception returns 500")
        void sadPath_exception() throws Exception {
            doThrow(new RuntimeException("Deactivation failed")).when(escalationService)
                    .deactivateRule(TENANT_ID, "bad-rule");

            mockMvc.perform(delete("/api/events/escalation-rules/{ruleId}", "bad-rule"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("Deactivation failed")));
        }
    }
}