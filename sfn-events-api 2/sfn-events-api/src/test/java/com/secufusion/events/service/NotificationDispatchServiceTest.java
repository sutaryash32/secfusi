package com.secufusion.events.service;

import com.secufusion.events.entity.Notification;
import com.secufusion.events.entity.NotificationPreference;
import com.secufusion.events.entity.User;
import com.secufusion.events.event.IncidentNotificationEvent;
import com.secufusion.events.repository.NotificationPreferenceRepository;
import com.secufusion.events.repository.NotificationRepository;
import com.secufusion.events.repository.UserRepository;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchService Tests")
class NotificationDispatchServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private NotificationEmailService notificationEmailService;

    @InjectMocks
    private NotificationDispatchService service;

    private static final String TENANT_ID = "tenant-1";
    private static final String ACTOR_KEYCLOAK_ID = "kc-actor";
    private static final String ACTOR_PK_ID     = "pk-actor";
    private static final String USER1_PK_ID     = "pk-user1";
    private static final String USER2_PK_ID     = "pk-user2";

    private User actorUser;
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        // Build actor user (for exclusion tests) – no tenantId setter needed
        actorUser = new User();
        actorUser.setPkUserId(ACTOR_PK_ID);
        actorUser.setKeycloakUserId(ACTOR_KEYCLOAK_ID);
        actorUser.setEmail("actor@example.com");

        // Ordinary users – only fields actually used by dispatch()
        user1 = new User();
        user1.setPkUserId(USER1_PK_ID);
        user1.setEmail("user1@example.com");

        user2 = new User();
        user2.setPkUserId(USER2_PK_ID);
        user2.setEmail("user2@example.com");
    }

    // Helper to create a base IncidentNotificationEvent
    private IncidentNotificationEvent buildEvent(List<String> targetUserIds) {
        return new IncidentNotificationEvent(
                "INCIDENT_CREATED",          // type
                TENANT_ID,                   // tenantId
                ACTOR_KEYCLOAK_ID,           // actorUserId (Keycloak)
                "incident-123",              // incidentId
                "INC-000001",                // incidentNumber
                "P2_HIGH",                   // priority
                "MALWARE",                   // category
                "HIGH",                      // severity
                "Test Notification",         // title
                "Description",               // message
                targetUserIds,               // targetUserIds
                null                         // metadata
        );
    }

    // ==================== dispatch ====================
    @Nested
    @DisplayName("dispatch")
    class DispatchTests {

        @Test
        @DisplayName("Happy Path – specific target users, no preferences, both channels")
        void happyPath_withTargetUsers_defaultPrefs() throws Exception {
            // ARRANGE
            IncidentNotificationEvent event = buildEvent(List.of(USER1_PK_ID, USER2_PK_ID));

            when(userRepository.findAllById(anyList()))
                    .thenReturn(List.of(user1, user2));
            // actor exclusion: actor not in these users
            when(userRepository.findByKeycloakUserIdAndTenant_TenantID(ACTOR_KEYCLOAK_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            // No preferences
            when(preferenceRepository.findByFkTenantIdAndCategoryIsNull(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            lenient().when(preferenceRepository.findByFkTenantIdAndCategory(TENANT_ID, "INCIDENT"))
                    .thenReturn(Collections.emptyList());

            lenient().when(notificationRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            service.dispatch(event);
            Thread.sleep(200); // wait for async emails

            // ASSERT
            ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationRepository).saveAll(captor.capture());
            List<Notification> saved = captor.getValue();
            assertEquals(2, saved.size());

            verify(notificationEmailService, timeout(1000).times(1))
                    .sendNotificationEmail(eq(event), eq(user1));
            verify(notificationEmailService, timeout(1000).times(1))
                    .sendNotificationEmail(eq(event), eq(user2));
        }

        @Test
        @DisplayName("Happy Path – actor excluded from target list")
        void happyPath_actorExcluded() throws Exception {
            // ARRANGE
            List<String> targetIds = List.of(ACTOR_PK_ID, USER1_PK_ID);
            IncidentNotificationEvent event = buildEvent(targetIds);
            when(userRepository.findAllById(anyList()))
                    .thenReturn(List.of(actorUser, user1));
            when(userRepository.findByKeycloakUserIdAndTenant_TenantID(ACTOR_KEYCLOAK_ID, TENANT_ID))
                    .thenReturn(Optional.of(actorUser));

            when(preferenceRepository.findByFkTenantIdAndCategoryIsNull(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            lenient().when(preferenceRepository.findByFkTenantIdAndCategory(TENANT_ID, "INCIDENT"))
                    .thenReturn(Collections.emptyList());
            lenient().when(notificationRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            service.dispatch(event);
            Thread.sleep(200);

            // ASSERT
            ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationRepository).saveAll(captor.capture());
            List<Notification> notifs = captor.getValue();
            assertEquals(1, notifs.size());
            assertEquals(USER1_PK_ID, notifs.get(0).getFkUserId());

            verify(notificationEmailService, timeout(1000).times(1))
                    .sendNotificationEmail(eq(event), eq(user1));
            verify(notificationEmailService, never())
                    .sendNotificationEmail(eq(event), eq(actorUser));
        }

        @Test
        @DisplayName("Happy Path – severity filter passes equal rank")
        void happyPath_severityFilterPasses() throws Exception {
            // ARRANGE
            IncidentNotificationEvent event = buildEvent(List.of(USER1_PK_ID));
            when(userRepository.findAllById(anyList()))
                    .thenReturn(List.of(user1));
            when(userRepository.findByKeycloakUserIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            NotificationPreference pref = new NotificationPreference();
            pref.setFkUserId(USER1_PK_ID);
            pref.setFkTenantId(TENANT_ID);
            pref.setMinSeverity("HIGH");
            pref.setInAppEnabled(true);
            pref.setEmailEnabled(true);

            when(preferenceRepository.findByFkTenantIdAndCategoryIsNull(TENANT_ID))
                    .thenReturn(List.of(pref));
            lenient().when(preferenceRepository.findByFkTenantIdAndCategory(TENANT_ID, "INCIDENT"))
                    .thenReturn(Collections.emptyList());
            lenient().when(notificationRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            service.dispatch(event);
            Thread.sleep(200);

            // ASSERT – user should receive because event severity HIGH meets minSeverity HIGH
            verify(notificationRepository).saveAll(anyList());
            verify(notificationEmailService, timeout(1000).times(1))
                    .sendNotificationEmail(eq(event), eq(user1));
        }

        @Test
        @DisplayName("Happy Path – severity filter skips when event too low")
        void happyPath_severityFilterSkips() throws Exception {
            // ARRANGE
            IncidentNotificationEvent event = buildEvent(List.of(USER1_PK_ID));
            when(userRepository.findAllById(anyList()))
                    .thenReturn(List.of(user1));
            when(userRepository.findByKeycloakUserIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            NotificationPreference pref = new NotificationPreference();
            pref.setFkUserId(USER1_PK_ID);
            pref.setFkTenantId(TENANT_ID);
            pref.setMinSeverity("CRITICAL"); // event is HIGH → will be skipped
            pref.setInAppEnabled(true);
            pref.setEmailEnabled(true);

            when(preferenceRepository.findByFkTenantIdAndCategoryIsNull(TENANT_ID))
                    .thenReturn(List.of(pref));
            lenient().when(preferenceRepository.findByFkTenantIdAndCategory(TENANT_ID, "INCIDENT"))
                    .thenReturn(Collections.emptyList());

            // ACT
            service.dispatch(event);
            Thread.sleep(200);

            // ASSERT – no notifications because severity too low
            verify(notificationRepository, never()).saveAll(anyList());
            verify(notificationEmailService, never()).sendNotificationEmail(any(), any());
        }

        @Test
        @DisplayName("Happy Path – no target users resolved")
        void happyPath_noTargetUsers() {
            // ARRANGE
            IncidentNotificationEvent event = buildEvent(null); // fallback to all tenant users
            when(userRepository.findByTenant_TenantIDOrderByUserNameAsc(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            // ACT
            service.dispatch(event);

            // ASSERT – no further processing
            verify(userRepository, never()).findByKeycloakUserIdAndTenant_TenantID(anyString(), anyString());
            verify(notificationRepository, never()).saveAll(anyList());
            verify(notificationEmailService, never()).sendNotificationEmail(any(), any());
        }

        @Test
        @DisplayName("Happy Path – all users filtered after actor exclusion")
        void happyPath_allFilteredAfterActorExclusion() {
            // ARRANGE
            IncidentNotificationEvent event = buildEvent(List.of(ACTOR_PK_ID));
            when(userRepository.findAllById(anyList()))
                    .thenReturn(List.of(actorUser));
            when(userRepository.findByKeycloakUserIdAndTenant_TenantID(ACTOR_KEYCLOAK_ID, TENANT_ID))
                    .thenReturn(Optional.of(actorUser));

            // ACT
            service.dispatch(event);

            // ASSERT
            verify(notificationRepository, never()).saveAll(anyList());
            verify(notificationEmailService, never()).sendNotificationEmail(any(), any());
        }

        @Test
        @DisplayName("Happy Path – email disabled, only in-app saved")
        void happyPath_emailDisabled() throws Exception {
            // ARRANGE
            IncidentNotificationEvent event = buildEvent(List.of(USER1_PK_ID));
            when(userRepository.findAllById(anyList()))
                    .thenReturn(List.of(user1));
            when(userRepository.findByKeycloakUserIdAndTenant_TenantID(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            NotificationPreference pref = new NotificationPreference();
            pref.setFkUserId(USER1_PK_ID);
            pref.setFkTenantId(TENANT_ID);
            pref.setEmailEnabled(false);
            pref.setInAppEnabled(true);

            when(preferenceRepository.findByFkTenantIdAndCategoryIsNull(TENANT_ID))
                    .thenReturn(List.of(pref));
            lenient().when(preferenceRepository.findByFkTenantIdAndCategory(TENANT_ID, "INCIDENT"))
                    .thenReturn(Collections.emptyList());
            lenient().when(notificationRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

            // ACT
            service.dispatch(event);
            Thread.sleep(200);

            // ASSERT
            verify(notificationRepository).saveAll(anyList());     // in-app saved
            verify(notificationEmailService, never()).sendNotificationEmail(any(), any()); // email skipped
        }
    }
}