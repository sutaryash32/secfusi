package com.secufusion.events.service;

import com.secufusion.events.dto.*;
import com.secufusion.events.entity.*;
import com.secufusion.events.exception.ResourceConflictException;
import com.secufusion.events.exception.ResourceNotFoundException;
import com.secufusion.events.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlaybookService Tests")
class PlaybookServiceTest {

    @Mock private PlaybookTemplateRepository templateRepository;
    @Mock private IncidentPlaybookRepository playbookRepository;
    @Mock private IncidentPlaybookStepRepository stepRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private IncidentActivityRepository activityRepository;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks
    private PlaybookService service;

    private static final String TENANT_ID = "t1";
    private static final String USER_ID = "u1";
    private static final String USER_NAME = "User";
    private static final String INCIDENT_ID = "inc-1";
    private static final String TEMPLATE_ID = "tmpl-1";
    private static final String PLAYBOOK_ID = "pb-1";
    private static final String STEP_ID = "step-1";

    private Tenant testTenant;
    private Incident testIncident;
    private PlaybookTemplate testTemplate;

    @BeforeEach
    void setUp() {
        testTenant = new Tenant();
        testTenant.setTenantID(TENANT_ID);

        testIncident = new Incident();
        testIncident.setPkIncidentId(INCIDENT_ID);
        testIncident.setTenant(testTenant);
        testIncident.setIncidentNumber("INC-000001");
        testIncident.setTitle("Test Incident");

        testTemplate = new PlaybookTemplate();
        testTemplate.setPkPlaybookTemplateId(TEMPLATE_ID);
        testTemplate.setTenant(testTenant);
        testTemplate.setName("Test Playbook");
        testTemplate.setDescription("Description");
        testTemplate.setCategory(IncidentCategory.MALWARE);
        testTemplate.setSteps(List.of(
                Map.of("title", "Step 1", "description", "Do first", "isRequired", "true"),
                Map.of("title", "Step 2", "description", "Do second", "isRequired", "false")
        ));
        testTemplate.setIsActive(true);
        testTemplate.setCreatedAt(Instant.now());
        testTemplate.setUpdatedAt(Instant.now());

        // Common lenient stubs
        lenient().when(activityRepository.save(any())).thenReturn(new IncidentActivity());
    }

    // ====================== createTemplate ======================
    @Nested
    @DisplayName("createTemplate")
    class CreateTemplateTests {

        @Test
        @DisplayName("Happy Path — template created")
        void happyPath() {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("New Template");
            req.setDescription("Desc");
            req.setCategory("MALWARE");
            req.setSteps(List.of(Map.of("title", "Step 1")));

            when(templateRepository.existsByTenant_TenantIDAndName(TENANT_ID, "New Template")).thenReturn(false);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
            when(templateRepository.save(any(PlaybookTemplate.class))).thenAnswer(inv -> {
                PlaybookTemplate t = inv.getArgument(0);
                t.setPkPlaybookTemplateId("new-tmpl");
                return t;
            });

            PlaybookTemplateDTO result = service.createTemplate(TENANT_ID, req);
            assertNotNull(result);
            assertEquals("New Template", result.getName());
            verify(templateRepository).save(any(PlaybookTemplate.class));
        }

        @Test
        @DisplayName("Sad Path — name already exists")
        void sadPath_duplicateName() {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("Duplicate");
            when(templateRepository.existsByTenant_TenantIDAndName(TENANT_ID, "Duplicate")).thenReturn(true);

            assertThrows(ResourceConflictException.class, () -> service.createTemplate(TENANT_ID, req));
        }

        @Test
        @DisplayName("Sad Path — tenant not found")
        void sadPath_tenantNotFound() {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("Test");
            when(templateRepository.existsByTenant_TenantIDAndName(anyString(), anyString())).thenReturn(false);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.createTemplate(TENANT_ID, req));
        }
    }

    // ====================== listTemplates ======================
    @Nested
    @DisplayName("listTemplates")
    class ListTemplatesTests {

        @Test
        @DisplayName("Happy Path — all templates")
        void happyPath_all() {
            when(templateRepository.findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(TENANT_ID))
                    .thenReturn(List.of(testTemplate));

            List<PlaybookTemplateDTO> result = service.listTemplates(TENANT_ID, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Happy Path — filtered by category")
        void happyPath_filtered() {
            when(templateRepository.findByTenant_TenantIDAndCategoryAndIsActiveTrueOrderByNameAsc(
                    eq(TENANT_ID), eq(IncidentCategory.MALWARE)))
                    .thenReturn(List.of(testTemplate));

            List<PlaybookTemplateDTO> result = service.listTemplates(TENANT_ID, "MALWARE");
            assertEquals(1, result.size());
        }
    }

    // ====================== getTemplate ======================
    @Nested
    @DisplayName("getTemplate")
    class GetTemplateTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(TEMPLATE_ID, TENANT_ID))
                    .thenReturn(Optional.of(testTemplate));

            PlaybookTemplateDTO result = service.getTemplate(TENANT_ID, TEMPLATE_ID);
            assertEquals("Test Playbook", result.getName());
        }

        @Test
        @DisplayName("Sad Path — not found")
        void sadPath_notFound() {
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> service.getTemplate(TENANT_ID, "bad"));
        }
    }

    // ====================== updateTemplate ======================
    @Nested
    @DisplayName("updateTemplate")
    class UpdateTemplateTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("Updated");
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(TEMPLATE_ID, TENANT_ID))
                    .thenReturn(Optional.of(testTemplate));
            when(templateRepository.save(any())).thenReturn(testTemplate);

            PlaybookTemplateDTO result = service.updateTemplate(TENANT_ID, TEMPLATE_ID, req);
            assertEquals("Updated", result.getName());
        }

        @Test
        @DisplayName("Sad Path — not found")
        void sadPath_notFound() {
            CreatePlaybookTemplateRequest req = new CreatePlaybookTemplateRequest();
            req.setName("x");
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> service.updateTemplate(TENANT_ID, "bad", req));
        }
    }

    // ====================== deactivateTemplate ======================
    @Nested
    @DisplayName("deactivateTemplate")
    class DeactivateTemplateTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(TEMPLATE_ID, TENANT_ID))
                    .thenReturn(Optional.of(testTemplate));
            when(templateRepository.save(any())).thenReturn(testTemplate);

            assertDoesNotThrow(() -> service.deactivateTemplate(TENANT_ID, TEMPLATE_ID));
            assertFalse(testTemplate.getIsActive());
        }

        @Test
        @DisplayName("Sad Path — not found")
        void sadPath_notFound() {
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> service.deactivateTemplate(TENANT_ID, "bad"));
        }
    }

    // ====================== attachPlaybook ======================
    @Nested
    @DisplayName("attachPlaybook")
    class AttachPlaybookTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            AttachPlaybookRequest req = new AttachPlaybookRequest();
            req.setTemplateId(TEMPLATE_ID);

            when(incidentRepository.findByPkIncidentIdAndTenant_TenantID(INCIDENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(testIncident));
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(TEMPLATE_ID, TENANT_ID))
                    .thenReturn(Optional.of(testTemplate));
            when(playbookRepository.existsByIncident_PkIncidentIdAndPlaybookTemplate_PkPlaybookTemplateId(
                    INCIDENT_ID, TEMPLATE_ID)).thenReturn(false);
            when(playbookRepository.save(any(IncidentPlaybook.class))).thenAnswer(inv -> {
                IncidentPlaybook pb = inv.getArgument(0);
                pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
                return pb;
            });
            when(stepRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            IncidentPlaybookDTO result = service.attachPlaybook(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, req);
            assertNotNull(result);
            assertEquals(PLAYBOOK_ID, result.getPlaybookId());
            assertEquals(2, result.getSteps().size());
        }

        @Test
        @DisplayName("Sad Path — incident not found")
        void sadPath_incidentNotFound() {
            AttachPlaybookRequest req = new AttachPlaybookRequest();
            req.setTemplateId(TEMPLATE_ID);
            when(incidentRepository.findByPkIncidentIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.attachPlaybook(TENANT_ID, USER_ID, USER_NAME, "bad", req));
        }

        @Test
        @DisplayName("Sad Path — template not found")
        void sadPath_templateNotFound() {
            AttachPlaybookRequest req = new AttachPlaybookRequest();
            req.setTemplateId("bad-tmpl");
            when(incidentRepository.findByPkIncidentIdAndTenant_TenantID(INCIDENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(testIncident));
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID("bad-tmpl", TENANT_ID))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                    () -> service.attachPlaybook(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, req));
        }

        @Test
        @DisplayName("Sad Path — playbook already attached")
        void sadPath_duplicate() {
            AttachPlaybookRequest req = new AttachPlaybookRequest();
            req.setTemplateId(TEMPLATE_ID);
            when(incidentRepository.findByPkIncidentIdAndTenant_TenantID(INCIDENT_ID, TENANT_ID))
                    .thenReturn(Optional.of(testIncident));
            when(templateRepository.findByPkPlaybookTemplateIdAndTenant_TenantID(TEMPLATE_ID, TENANT_ID))
                    .thenReturn(Optional.of(testTemplate));
            when(playbookRepository.existsByIncident_PkIncidentIdAndPlaybookTemplate_PkPlaybookTemplateId(
                    INCIDENT_ID, TEMPLATE_ID)).thenReturn(true);
            assertThrows(ResourceConflictException.class,
                    () -> service.attachPlaybook(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, req));
        }
    }

    // ====================== getIncidentPlaybooks ======================
    @Nested
    @DisplayName("getIncidentPlaybooks")
    class GetIncidentPlaybooksTests {

        @Test
        @DisplayName("Happy Path — returns playbooks with steps")
        void happyPath() {
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            pb.setPlaybookTemplate(testTemplate);
            pb.setTotalSteps(2);
            pb.setCompletedSteps(0);
            pb.setIsComplete(false);
            pb.setCreatedAt(Instant.now());

            IncidentPlaybookStep s1 = new IncidentPlaybookStep();
            s1.setPkIncidentPlaybookStepId("s1");
            s1.setStepNumber(1);
            s1.setTitle("Step 1");
            s1.setIsCompleted(false);

            when(playbookRepository.findByIncident_PkIncidentIdAndTenantIdOrderByCreatedAtDesc(INCIDENT_ID, TENANT_ID))
                    .thenReturn(List.of(pb));
            when(stepRepository.findByIncidentPlaybook_PkIncidentPlaybookIdAndTenantIdOrderByStepNumberAsc(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(List.of(s1));

            List<IncidentPlaybookDTO> result = service.getIncidentPlaybooks(TENANT_ID, INCIDENT_ID);
            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getSteps().size());
        }
    }

    // ====================== completeStep ======================
    @Nested
    @DisplayName("completeStep")
    class CompleteStepTests {

        @Test
        @DisplayName("Happy Path — step completed, playbook not fully done")
        void happyPath_partial() {
            // Setup playbook with 2 steps, only one completed before
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            pb.setIncident(testIncident);
            pb.setPlaybookTemplate(testTemplate);
            pb.setTotalSteps(2);
            pb.setCompletedSteps(0);
            pb.setIsComplete(false);

            IncidentPlaybookStep step = new IncidentPlaybookStep();
            step.setPkIncidentPlaybookStepId(STEP_ID);
            step.setStepNumber(1);
            step.setTitle("Step 1");
            step.setIsCompleted(false);
            step.setIsRequired(true);

            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(Optional.of(pb));
            when(stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(STEP_ID, TENANT_ID))
                    .thenReturn(Optional.of(step));
            when(stepRepository.save(any())).thenReturn(step);
            when(stepRepository.countByIncidentPlaybook_PkIncidentPlaybookIdAndIsCompletedTrue(PLAYBOOK_ID))
                    .thenReturn(1L); // one completed now
            when(playbookRepository.save(any())).thenReturn(pb);

            CompleteStepRequest req = new CompleteStepRequest();
            req.setNotes("Done");

            IncidentPlaybookStepDTO result = service.completeStep(TENANT_ID, USER_ID, USER_NAME,
                    INCIDENT_ID, PLAYBOOK_ID, STEP_ID, req);
            assertTrue(result.getIsCompleted());
            assertNotNull(result.getCompletedAt());
            assertEquals("Done", result.getNotes());
        }

        @Test
        @DisplayName("Happy Path — all steps complete, playbook marked complete")
        void happyPath_allComplete() {
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            pb.setIncident(testIncident);
            pb.setPlaybookTemplate(testTemplate);
            pb.setTotalSteps(2);
            pb.setCompletedSteps(1);
            pb.setIsComplete(false);

            IncidentPlaybookStep step = new IncidentPlaybookStep();
            step.setPkIncidentPlaybookStepId(STEP_ID);
            step.setStepNumber(2);
            step.setTitle("Step 2");
            step.setIsCompleted(false);

            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(Optional.of(pb));
            when(stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(STEP_ID, TENANT_ID))
                    .thenReturn(Optional.of(step));
            when(stepRepository.save(any())).thenReturn(step);
            when(stepRepository.countByIncidentPlaybook_PkIncidentPlaybookIdAndIsCompletedTrue(PLAYBOOK_ID))
                    .thenReturn(2L); // all done
            when(playbookRepository.save(any())).thenReturn(pb);

            IncidentPlaybookStepDTO result = service.completeStep(TENANT_ID, USER_ID, USER_NAME,
                    INCIDENT_ID, PLAYBOOK_ID, STEP_ID, null);
            assertTrue(result.getIsCompleted());
        }

        @Test
        @DisplayName("Sad Path — step already completed")
        void sadPath_alreadyCompleted() {
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            IncidentPlaybookStep step = new IncidentPlaybookStep();
            step.setPkIncidentPlaybookStepId(STEP_ID);
            step.setIsCompleted(true); // already done

            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(Optional.of(pb));
            when(stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(STEP_ID, TENANT_ID))
                    .thenReturn(Optional.of(step));

            assertThrows(IllegalStateException.class, () ->
                    service.completeStep(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, PLAYBOOK_ID, STEP_ID, null));
        }

        @Test
        @DisplayName("Sad Path — playbook not found")
        void sadPath_playbookNotFound() {
            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () ->
                    service.completeStep(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, "bad", "bad", null));
        }

        @Test
        @DisplayName("Sad Path — step not found")
        void sadPath_stepNotFound() {
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(Optional.of(pb));
            when(stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () ->
                    service.completeStep(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, PLAYBOOK_ID, "bad", null));
        }
    }

    // ====================== uncompleteStep ======================
    @Nested
    @DisplayName("uncompleteStep")
    class UncompleteStepTests {

        @Test
        @DisplayName("Happy Path")
        void happyPath() {
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            pb.setTotalSteps(2);
            pb.setCompletedSteps(2);
            pb.setIsComplete(true);

            IncidentPlaybookStep step = new IncidentPlaybookStep();
            step.setPkIncidentPlaybookStepId(STEP_ID);
            step.setIsCompleted(true);
            step.setCompletedBy(USER_ID);
            step.setCompletedByName(USER_NAME);
            step.setCompletedAt(Instant.now());
            step.setNotes("Old notes");

            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(Optional.of(pb));
            when(stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(STEP_ID, TENANT_ID))
                    .thenReturn(Optional.of(step));
            when(stepRepository.save(any())).thenReturn(step);
            when(stepRepository.countByIncidentPlaybook_PkIncidentPlaybookIdAndIsCompletedTrue(PLAYBOOK_ID))
                    .thenReturn(1L);
            when(playbookRepository.save(any())).thenReturn(pb);

            IncidentPlaybookStepDTO result = service.uncompleteStep(TENANT_ID, USER_ID, USER_NAME,
                    INCIDENT_ID, PLAYBOOK_ID, STEP_ID);
            assertFalse(result.getIsCompleted());
            assertNull(result.getCompletedBy());
            assertNull(result.getCompletedAt());
            assertNull(result.getNotes());
        }

        @Test
        @DisplayName("Sad Path — step not completed")
        void sadPath_notCompleted() {
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            IncidentPlaybookStep step = new IncidentPlaybookStep();
            step.setPkIncidentPlaybookStepId(STEP_ID);
            step.setIsCompleted(false); // not completed

            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(Optional.of(pb));
            when(stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(STEP_ID, TENANT_ID))
                    .thenReturn(Optional.of(step));

            assertThrows(IllegalStateException.class, () ->
                    service.uncompleteStep(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, PLAYBOOK_ID, STEP_ID));
        }

        @Test
        @DisplayName("Sad Path — playbook not found")
        void sadPath_playbookNotFound() {
            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () ->
                    service.uncompleteStep(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, "bad", "bad"));
        }

        @Test
        @DisplayName("Sad Path — step not found")
        void sadPath_stepNotFound() {
            IncidentPlaybook pb = new IncidentPlaybook();
            pb.setPkIncidentPlaybookId(PLAYBOOK_ID);
            when(playbookRepository.findByPkIncidentPlaybookIdAndTenantId(PLAYBOOK_ID, TENANT_ID))
                    .thenReturn(Optional.of(pb));
            when(stepRepository.findByPkIncidentPlaybookStepIdAndTenantId(anyString(), anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () ->
                    service.uncompleteStep(TENANT_ID, USER_ID, USER_NAME, INCIDENT_ID, PLAYBOOK_ID, "bad"));
        }
    }
}