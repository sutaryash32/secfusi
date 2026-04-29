package com.secufusion.events.service;

import com.secufusion.events.dto.CreateEscalationRuleRequest;
import com.secufusion.events.dto.EscalationRuleDTO;
import com.secufusion.events.entity.*;
import com.secufusion.events.event.IncidentNotificationEvent;
import com.secufusion.events.exception.ResourceConflictException;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.EscalationRuleRepository;
import com.secufusion.events.repository.IncidentActivityRepository;
import com.secufusion.events.repository.IncidentRepository;
import com.secufusion.events.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EscalationService Tests")
class EscalationServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────
    @Mock private EscalationRuleRepository      escalationRuleRepository;
    @Mock private IncidentRepository            incidentRepository;
    @Mock private IncidentActivityRepository    incidentActivityRepository;
    @Mock private TenantRepository              tenantRepository;
    @Mock private NotificationDispatchService   notificationDispatchService;

    @InjectMocks
    private EscalationService escalationService;

    // ── Common test data ─────────────────────────────────────────────────────
    private static final String TENANT_ID = "tenant-001";
    private static final String RULE_ID   = "rule-abc";
    private static final String RULE_NAME = "Critical Auto-Escalate";

    private Tenant                      mockTenant;
    private EscalationRule              mockRule;
    private CreateEscalationRuleRequest createRequest;

    @BeforeEach
    void setUp() {
        // ✅ Fix 1: use setTenantName instead of setName
        mockTenant = new Tenant();
        mockTenant.setTenantID(TENANT_ID);
        mockTenant.setTenantName("Test Corp");

        // ✅ Fix 2: remove createdAt/updatedAt from builder, set via setters
        mockRule = EscalationRule.builder()
                .pkEscalationRuleId(RULE_ID)
                .tenant(mockTenant)
                .name(RULE_NAME)
                .description("Auto-escalate critical incidents")
                .triggerPriority(IncidentPriority.P2_HIGH)
                .unassignedMinutes(30)
                .unresolvedHours(4)
                .escalateToPriority(IncidentPriority.P1_CRITICAL)
                .notifyRole("ADMIN")
                .isActive(true)
                .build();
        mockRule.setCreatedAt(Instant.now());
        mockRule.setUpdatedAt(Instant.now());

        createRequest = new CreateEscalationRuleRequest();
        createRequest.setName(RULE_NAME);
        createRequest.setDescription("Auto-escalate critical incidents");
        createRequest.setTriggerPriority("P2_HIGH");
        createRequest.setUnassignedMinutes(30);
        createRequest.setUnresolvedHours(4);
        createRequest.setEscalateToPriority("P1_CRITICAL");
        createRequest.setNotifyRole("ADMIN");
    }

    // ── Helper: build a minimal Incident ─────────────────────────────────────
    private Incident buildIncident(String id, String number, IncidentPriority priority) {
        Incident inc = new Incident();
        inc.setPkIncidentId(id);
        inc.setIncidentNumber(number);
        inc.setPriority(priority);
        inc.setTitle("Test incident " + number);
        inc.setCategory(IncidentCategory.UNAUTHORIZED_ACCESS); // ✅ Fix 3: valid enum value
        inc.setEscalatedAt(null);
        return inc;
    }

    // =========================================================================
    // createRule
    // =========================================================================

    @Nested
    @DisplayName("createRule")
    class CreateRule {

        @Test
        @DisplayName("Happy Path — rule created and DTO returned")
        void happyPath_ruleCreatedAndDTOReturned() {
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(escalationRuleRepository.existsByTenant_TenantIDAndName(TENANT_ID, RULE_NAME))
                    .thenReturn(false);
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(mockRule);

            EscalationRuleDTO result = escalationService.createRule(TENANT_ID, createRequest);

            assertNotNull(result);
            assertEquals(RULE_ID,        result.getRuleId());
            assertEquals(RULE_NAME,      result.getName());
            assertEquals("P2_HIGH",      result.getTriggerPriority());
            assertEquals("P1_CRITICAL",  result.getEscalateToPriority());
            assertEquals(30,             result.getUnassignedMinutes());
            assertEquals(4,              result.getUnresolvedHours());
            assertEquals("ADMIN",        result.getNotifyRole());
            assertTrue(result.getIsActive());
            assertNotNull(result.getCreatedAt());
            assertNotNull(result.getUpdatedAt());

            verify(tenantRepository).findByTenantID(TENANT_ID);
            verify(escalationRuleRepository).existsByTenant_TenantIDAndName(TENANT_ID, RULE_NAME);
            verify(escalationRuleRepository).save(any(EscalationRule.class));
        }

        @Test
        @DisplayName("Happy Path — null triggerPriority and escalateToPriority stored as null")
        void happyPath_nullPriorities_storedAsNull() {
            createRequest.setTriggerPriority(null);
            createRequest.setEscalateToPriority(null);

            // ✅ No createdAt/updatedAt in builder
            EscalationRule ruleWithNullPriority = EscalationRule.builder()
                    .pkEscalationRuleId(RULE_ID)
                    .tenant(mockTenant)
                    .name(RULE_NAME)
                    .triggerPriority(null)
                    .escalateToPriority(null)
                    .isActive(true)
                    .build();

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(escalationRuleRepository.existsByTenant_TenantIDAndName(TENANT_ID, RULE_NAME))
                    .thenReturn(false);
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(ruleWithNullPriority);

            EscalationRuleDTO result = escalationService.createRule(TENANT_ID, createRequest);

            assertNotNull(result);
            assertNull(result.getTriggerPriority());
            assertNull(result.getEscalateToPriority());
        }

        @Test
        @DisplayName("Happy Path — invalid priority string parsed as null (no crash)")
        void happyPath_invalidPriorityString_parsedAsNull() {
            createRequest.setTriggerPriority("NOT_A_PRIORITY");
            createRequest.setEscalateToPriority("GARBAGE");

            // ✅ No createdAt/updatedAt in builder
            EscalationRule ruleWithNullPriority = EscalationRule.builder()
                    .pkEscalationRuleId(RULE_ID)
                    .tenant(mockTenant)
                    .name(RULE_NAME)
                    .triggerPriority(null)
                    .escalateToPriority(null)
                    .isActive(true)
                    .build();

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(escalationRuleRepository.existsByTenant_TenantIDAndName(TENANT_ID, RULE_NAME))
                    .thenReturn(false);
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(ruleWithNullPriority);

            EscalationRuleDTO result = escalationService.createRule(TENANT_ID, createRequest);

            assertNotNull(result);
            assertNull(result.getTriggerPriority());
        }

        @Test
        @DisplayName("Sad Path — tenant not found throws ResourceNotFoundException")
        void sadPath_tenantNotFound_throwsException() {
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> escalationService.createRule(TENANT_ID, createRequest));

            verify(escalationRuleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sad Path — duplicate rule name throws ResourceConflictException")
        void sadPath_duplicateName_throwsConflict() {
            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(escalationRuleRepository.existsByTenant_TenantIDAndName(TENANT_ID, RULE_NAME))
                    .thenReturn(true);

            assertThrows(ResourceConflictException.class,
                    () -> escalationService.createRule(TENANT_ID, createRequest));

            verify(escalationRuleRepository, never()).save(any());
        }
    }

    // =========================================================================
    // listRules
    // =========================================================================

    @Nested
    @DisplayName("listRules")
    class ListRules {

        @Test
        @DisplayName("Happy Path — returns mapped DTOs for active rules")
        void happyPath_returnsMappedDTOs() {
            when(escalationRuleRepository
                    .findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(TENANT_ID))
                    .thenReturn(List.of(mockRule));

            List<EscalationRuleDTO> result = escalationService.listRules(TENANT_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(RULE_NAME, result.get(0).getName());
            verify(escalationRuleRepository)
                    .findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(TENANT_ID);
        }

        @Test
        @DisplayName("Happy Path — no active rules returns empty list")
        void happyPath_noRules_returnsEmpty() {
            when(escalationRuleRepository
                    .findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            List<EscalationRuleDTO> result = escalationService.listRules(TENANT_ID);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // updateRule
    // =========================================================================

    @Nested
    @DisplayName("updateRule")
    class UpdateRule {

        @Test
        @DisplayName("Happy Path — all fields updated and DTO returned")
        void happyPath_allFieldsUpdated() {
            CreateEscalationRuleRequest updateReq = new CreateEscalationRuleRequest();
            updateReq.setName("Updated Rule");
            updateReq.setDescription("Updated desc");
            updateReq.setTriggerPriority("P1_CRITICAL");
            updateReq.setUnassignedMinutes(60);
            updateReq.setUnresolvedHours(8);
            updateReq.setEscalateToPriority("P1_CRITICAL");
            updateReq.setNotifyRole("SUPER_ADMIN");

            when(escalationRuleRepository
                    .findByPkEscalationRuleIdAndTenant_TenantID(RULE_ID, TENANT_ID))
                    .thenReturn(Optional.of(mockRule));
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(mockRule);

            EscalationRuleDTO result = escalationService.updateRule(TENANT_ID, RULE_ID, updateReq);

            assertNotNull(result);
            verify(escalationRuleRepository).save(mockRule);
            assertEquals("Updated Rule", mockRule.getName());
            assertEquals("Updated desc", mockRule.getDescription());
            assertEquals(60,             mockRule.getUnassignedMinutes());
            assertEquals(8,              mockRule.getUnresolvedHours());
            assertEquals("SUPER_ADMIN",  mockRule.getNotifyRole());
        }

        @Test
        @DisplayName("Happy Path — null fields in request leave entity unchanged")
        void happyPath_nullFields_entityUnchanged() {
            CreateEscalationRuleRequest partialReq = new CreateEscalationRuleRequest();

            when(escalationRuleRepository
                    .findByPkEscalationRuleIdAndTenant_TenantID(RULE_ID, TENANT_ID))
                    .thenReturn(Optional.of(mockRule));
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(mockRule);

            EscalationRuleDTO result = escalationService.updateRule(TENANT_ID, RULE_ID, partialReq);

            assertNotNull(result);
            assertEquals(RULE_NAME,                    mockRule.getName());
            assertEquals(IncidentPriority.P2_HIGH,     mockRule.getTriggerPriority());
            assertEquals(IncidentPriority.P1_CRITICAL, mockRule.getEscalateToPriority());
        }

        @Test
        @DisplayName("Sad Path — rule not found throws ResourceNotFoundException")
        void sadPath_ruleNotFound_throwsException() {
            when(escalationRuleRepository
                    .findByPkEscalationRuleIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> escalationService.updateRule(TENANT_ID, "bad-id", createRequest));
            verify(escalationRuleRepository, never()).save(any());
        }
    }

    // =========================================================================
    // deactivateRule
    // =========================================================================

    @Nested
    @DisplayName("deactivateRule")
    class DeactivateRule {

        @Test
        @DisplayName("Happy Path — rule isActive set to false and saved")
        void happyPath_ruleDeactivated() {
            when(escalationRuleRepository
                    .findByPkEscalationRuleIdAndTenant_TenantID(RULE_ID, TENANT_ID))
                    .thenReturn(Optional.of(mockRule));
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(mockRule);

            escalationService.deactivateRule(TENANT_ID, RULE_ID);

            assertFalse(mockRule.getIsActive());
            verify(escalationRuleRepository).save(mockRule);
        }

        @Test
        @DisplayName("Sad Path — rule not found throws ResourceNotFoundException")
        void sadPath_ruleNotFound_throwsException() {
            when(escalationRuleRepository
                    .findByPkEscalationRuleIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> escalationService.deactivateRule(TENANT_ID, "bad-id"));
            verify(escalationRuleRepository, never()).save(any());
        }
    }

    // =========================================================================
    // processEscalations
    // =========================================================================

    @Nested
    @DisplayName("processEscalations")
    class ProcessEscalations {

        @Test
        @DisplayName("Happy Path — no active rules; returns early with no work done")
        void happyPath_noActiveRules_earlyReturn() {
            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(Collections.emptyList());

            escalationService.processEscalations();

            verifyNoInteractions(incidentRepository);
            verifyNoInteractions(incidentActivityRepository);
            verifyNoInteractions(notificationDispatchService);
        }

        @Test
        @DisplayName("Happy Path — active rule with candidates escalates incidents")
        void happyPath_activeRuleWithCandidates_escalatesIncidents() {
            Incident incident = buildIncident("inc-001", "INC-001", IncidentPriority.P2_HIGH);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    eq(TENANT_ID), anyString(), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(incident));
            when(incidentRepository.save(any(Incident.class)))
                    .thenReturn(incident);
            when(incidentActivityRepository.save(any(IncidentActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(notificationDispatchService)
                    .dispatch(any(IncidentNotificationEvent.class));

            escalationService.processEscalations();

            assertEquals(IncidentPriority.P1_CRITICAL, incident.getPriority());
            assertNotNull(incident.getEscalatedAt());
            verify(incidentRepository).save(incident);
            verify(incidentActivityRepository).save(any(IncidentActivity.class));
            verify(notificationDispatchService).dispatch(any(IncidentNotificationEvent.class));
        }

        @Test
        @DisplayName("Happy Path — multiple tenants processed independently")
        void happyPath_multipleTenantsProcessed() {
            // ✅ Fix: use setTenantName for tenantB too
            Tenant tenantB = new Tenant();
            tenantB.setTenantID("tenant-002");
            tenantB.setTenantName("Corp B");

            EscalationRule ruleB = EscalationRule.builder()
                    .pkEscalationRuleId("rule-b")
                    .tenant(tenantB)
                    .name("RuleB")
                    .triggerPriority(IncidentPriority.P3_MEDIUM)
                    .unassignedMinutes(60)
                    .unresolvedHours(8)
                    .escalateToPriority(IncidentPriority.P2_HIGH)
                    .isActive(true)
                    .build();

            Incident incA = buildIncident("inc-a", "INC-A", IncidentPriority.P2_HIGH);
            Incident incB = buildIncident("inc-b", "INC-B", IncidentPriority.P3_MEDIUM);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule, ruleB));
            when(incidentRepository.findEscalationCandidates(
                    eq(TENANT_ID), anyString(), any(), any()))
                    .thenReturn(List.of(incA));
            when(incidentRepository.findEscalationCandidates(
                    eq("tenant-002"), anyString(), any(), any()))
                    .thenReturn(List.of(incB));
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(incidentActivityRepository.save(any(IncidentActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(notificationDispatchService).dispatch(any());

            escalationService.processEscalations();

            verify(incidentRepository, times(2)).save(any(Incident.class));
            verify(incidentActivityRepository, times(2)).save(any(IncidentActivity.class));
            verify(notificationDispatchService, times(2)).dispatch(any());
        }

        @Test
        @DisplayName("Happy Path — active rule with no candidates performs no escalation")
        void happyPath_activeRuleNoCandidates_noEscalation() {
            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    anyString(), anyString(), any(), any()))
                    .thenReturn(Collections.emptyList());

            escalationService.processEscalations();

            verify(incidentRepository, never()).save(any());
            verifyNoInteractions(incidentActivityRepository);
            verifyNoInteractions(notificationDispatchService);
        }

        @Test
        @DisplayName("Happy Path — rule with null triggerPriority passes null priorityFilter")
        void happyPath_nullTriggerPriority_passesNullFilter() {
            mockRule.setTriggerPriority(null);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    eq(TENANT_ID), isNull(), any(Instant.class), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            escalationService.processEscalations();

            verify(incidentRepository).findEscalationCandidates(
                    eq(TENANT_ID), isNull(), any(Instant.class), any(Instant.class));
        }

        @Test
        @DisplayName("Happy Path — rule with null unassignedMinutes uses now as threshold")
        void happyPath_nullUnassignedMinutes_usesNowAsThreshold() {
            mockRule.setUnassignedMinutes(null);
            mockRule.setUnresolvedHours(null);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    anyString(), anyString(), any(Instant.class), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            escalationService.processEscalations();

            verify(incidentRepository).findEscalationCandidates(
                    anyString(), anyString(), any(Instant.class), any(Instant.class));
        }

        @Test
        @DisplayName("Sad Path — one incident escalation throws; others still processed")
        void sadPath_oneIncidentThrows_othersStillProcessed() {
            Incident goodIncident = buildIncident("inc-good", "INC-GOOD", IncidentPriority.P2_HIGH);
            Incident badIncident  = buildIncident("inc-bad",  "INC-BAD",  IncidentPriority.P2_HIGH);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    anyString(), anyString(), any(), any()))
                    .thenReturn(List.of(badIncident, goodIncident));
            when(incidentRepository.save(any(Incident.class)))
                    .thenThrow(new RuntimeException("DB error"))
                    .thenReturn(goodIncident);
            when(incidentActivityRepository.save(any(IncidentActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(notificationDispatchService).dispatch(any());

            assertDoesNotThrow(() -> escalationService.processEscalations());

            verify(incidentRepository, times(2)).save(any(Incident.class));
        }
    }

    // =========================================================================
    // escalateIncident — via processEscalations (private; tested indirectly)
    // =========================================================================

    @Nested
    @DisplayName("escalateIncident — branch coverage via processEscalations")
    class EscalateIncident {

        @Test
        @DisplayName("Happy Path — escalateToPriority null keeps original priority")
        void happyPath_nullEscalateToPriority_originalPriorityKept() {
            mockRule.setEscalateToPriority(null);
            Incident incident = buildIncident("inc-001", "INC-001", IncidentPriority.P2_HIGH);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    anyString(), anyString(), any(), any()))
                    .thenReturn(List.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
            when(incidentActivityRepository.save(any(IncidentActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(notificationDispatchService).dispatch(any());

            escalationService.processEscalations();

            assertEquals(IncidentPriority.P2_HIGH, incident.getPriority());

            ArgumentCaptor<IncidentActivity> actCaptor =
                    ArgumentCaptor.forClass(IncidentActivity.class);
            verify(incidentActivityRepository).save(actCaptor.capture());
            IncidentActivity saved = actCaptor.getValue();
            assertEquals(IncidentActivityAction.ESCALATED, saved.getAction());
            assertEquals("SYSTEM",            saved.getPerformedBy());
            assertEquals("Escalation Engine", saved.getPerformedByName());
            assertEquals("P2_HIGH",           saved.getOldValue());
            assertEquals("P2_HIGH",           saved.getNewValue());
        }

        @Test
        @DisplayName("Happy Path — incident category null sets null category in notification")
        void happyPath_nullCategory_nullInNotification() {
            Incident incident = buildIncident("inc-001", "INC-001", IncidentPriority.P2_HIGH);
            incident.setCategory(null);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    anyString(), anyString(), any(), any()))
                    .thenReturn(List.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
            when(incidentActivityRepository.save(any(IncidentActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(notificationDispatchService).dispatch(any());

            assertDoesNotThrow(() -> escalationService.processEscalations());
        }

        @Test
        @DisplayName("Sad Path — notificationDispatch throws; escalation still completes")
        void sadPath_notificationThrows_escalationStillCompletes() {
            Incident incident = buildIncident("inc-001", "INC-001", IncidentPriority.P2_HIGH);

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(mockRule));
            when(incidentRepository.findEscalationCandidates(
                    anyString(), anyString(), any(), any()))
                    .thenReturn(List.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
            when(incidentActivityRepository.save(any(IncidentActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Notification service down"))
                    .when(notificationDispatchService).dispatch(any());

            assertDoesNotThrow(() -> escalationService.processEscalations());

            verify(incidentRepository).save(incident);
            verify(incidentActivityRepository).save(any(IncidentActivity.class));
        }
    }

    // =========================================================================
    // mapPriorityToSeverity — via notification path
    // =========================================================================

    @Nested
    @DisplayName("mapPriorityToSeverity — all priority branches")
    class MapPriorityToSeverity {

        private void runEscalationWithPriority(IncidentPriority priority) {
            Incident incident = buildIncident("inc-x", "INC-X", priority);

            EscalationRule rule = EscalationRule.builder()
                    .pkEscalationRuleId("rule-x")
                    .tenant(mockTenant)
                    .name("Rule-X")
                    .triggerPriority(priority)
                    .unassignedMinutes(10)
                    .unresolvedHours(2)
                    .escalateToPriority(null)
                    .isActive(true)
                    .build();

            when(escalationRuleRepository.findByIsActiveTrue())
                    .thenReturn(List.of(rule));
            when(incidentRepository.findEscalationCandidates(
                    anyString(), anyString(), any(), any()))
                    .thenReturn(List.of(incident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
            when(incidentActivityRepository.save(any(IncidentActivity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<IncidentNotificationEvent> captor =
                    ArgumentCaptor.forClass(IncidentNotificationEvent.class);
            doNothing().when(notificationDispatchService).dispatch(captor.capture());

            escalationService.processEscalations();
        }

        @Test
        @DisplayName("Branch — P1_CRITICAL maps to CRITICAL severity")
        void branch_p1Critical_mapsToCritical() {
            runEscalationWithPriority(IncidentPriority.P1_CRITICAL);

            ArgumentCaptor<IncidentNotificationEvent> captor =
                    ArgumentCaptor.forClass(IncidentNotificationEvent.class);
            verify(notificationDispatchService).dispatch(captor.capture());
            assertEquals("CRITICAL", captor.getValue().getSeverity());
        }

        @Test
        @DisplayName("Branch — P2_HIGH maps to HIGH severity")
        void branch_p2High_mapsToHigh() {
            runEscalationWithPriority(IncidentPriority.P2_HIGH);

            ArgumentCaptor<IncidentNotificationEvent> captor =
                    ArgumentCaptor.forClass(IncidentNotificationEvent.class);
            verify(notificationDispatchService).dispatch(captor.capture());
            assertEquals("HIGH", captor.getValue().getSeverity());
        }

        @Test
        @DisplayName("Branch — P3_MEDIUM maps to MEDIUM severity")
        void branch_p3Medium_mapsToMedium() {
            runEscalationWithPriority(IncidentPriority.P3_MEDIUM);

            ArgumentCaptor<IncidentNotificationEvent> captor =
                    ArgumentCaptor.forClass(IncidentNotificationEvent.class);
            verify(notificationDispatchService).dispatch(captor.capture());
            assertEquals("MEDIUM", captor.getValue().getSeverity());
        }

        @Test
        @DisplayName("Branch — P4_LOW maps to LOW severity")
        void branch_p4Low_mapsToLow() {
            runEscalationWithPriority(IncidentPriority.P4_LOW);

            ArgumentCaptor<IncidentNotificationEvent> captor =
                    ArgumentCaptor.forClass(IncidentNotificationEvent.class);
            verify(notificationDispatchService).dispatch(captor.capture());
            assertEquals("LOW", captor.getValue().getSeverity());
        }
    }

    // =========================================================================
    // convertToDTO — null timestamps branch
    // =========================================================================

    @Nested
    @DisplayName("convertToDTO — null timestamp branches")
    class ConvertToDTO {

        @Test
        @DisplayName("Happy Path — null createdAt and updatedAt produce null strings in DTO")
        void happyPath_nullTimestamps_nullStringsInDTO() {
            // ✅ Fix: createdAt/updatedAt not in builder — entity has null by default
            EscalationRule ruleNoTimestamps = EscalationRule.builder()
                    .pkEscalationRuleId(RULE_ID)
                    .tenant(mockTenant)
                    .name(RULE_NAME)
                    .isActive(true)
                    .build();
            // createdAt and updatedAt are null by default — no setter needed

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(escalationRuleRepository.existsByTenant_TenantIDAndName(TENANT_ID, RULE_NAME))
                    .thenReturn(false);
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(ruleNoTimestamps);

            EscalationRuleDTO result = escalationService.createRule(TENANT_ID, createRequest);

            assertNotNull(result);
            assertNull(result.getCreatedAt());
            assertNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("Happy Path — null triggerPriority and escalateToPriority produce null strings in DTO")
        void happyPath_nullPriorityFields_nullStringsInDTO() {
            // ✅ Fix: createdAt/updatedAt set via setters, not builder
            EscalationRule ruleNoPriorities = EscalationRule.builder()
                    .pkEscalationRuleId(RULE_ID)
                    .tenant(mockTenant)
                    .name(RULE_NAME)
                    .triggerPriority(null)
                    .escalateToPriority(null)
                    .isActive(true)
                    .build();
            ruleNoPriorities.setCreatedAt(Instant.now());
            ruleNoPriorities.setUpdatedAt(Instant.now());

            when(tenantRepository.findByTenantID(TENANT_ID))
                    .thenReturn(Optional.of(mockTenant));
            when(escalationRuleRepository.existsByTenant_TenantIDAndName(TENANT_ID, RULE_NAME))
                    .thenReturn(false);
            when(escalationRuleRepository.save(any(EscalationRule.class)))
                    .thenReturn(ruleNoPriorities);

            EscalationRuleDTO result = escalationService.createRule(TENANT_ID, createRequest);

            assertNull(result.getTriggerPriority());
            assertNull(result.getEscalateToPriority());
            assertNotNull(result.getCreatedAt());
        }
    }
}