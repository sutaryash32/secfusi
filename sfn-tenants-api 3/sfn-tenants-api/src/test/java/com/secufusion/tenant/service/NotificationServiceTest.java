package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.NotificationEvent;
import com.secufusion.tenant.dto.NotificationPreferenceDTO;
import com.secufusion.tenant.dto.UnreadCountDTO;
import com.secufusion.tenant.entity.Notification;
import com.secufusion.tenant.entity.NotificationPreference;
import com.secufusion.tenant.entity.User;
import com.secufusion.tenant.exception.ResourceNotFoundException;
import com.secufusion.tenant.repository.NotificationPreferenceRepository;
import com.secufusion.tenant.repository.NotificationRepository;
import com.secufusion.tenant.repository.UserRepository;
import com.secufusion.tenant.util.NotificationWsPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationWsPublisher wsPublisher;
    @Mock
    private NotificationEmailService notificationEmailService;
    @Mock
    private WebhookService webhookService;

    @InjectMocks
    private NotificationService notificationService;

    private User user1;
    private User user2;
    private NotificationEvent event;

    @BeforeEach
    void setUp() {
        user1 = new User();
        user1.setPkUserId("u1");
        user1.setKeycloakUserId("kc-u1");
        user1.setEmail("u1@example.com");

        user2 = new User();
        user2.setPkUserId("u2");
        user2.setKeycloakUserId("kc-u2");
        user2.setEmail("u2@example.com");

        event = NotificationEvent.builder()
                .type("INCIDENT_CREATED")
                .tenantId("tenant-1")
                .severity("HIGH")
                .title("Incident")
                .message("Msg")
                .sourceService("events")
                .targetUserIds(List.of("u1", "u2"))
                .channels(null)
                .timestamp(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        void broadcastsOnly_whenNoTargetUsersResolved() {
            // ARRANGE
            when(userRepository.findAllById(anyList())).thenReturn(List.of());

            // ACT
            notificationService.dispatch(event);

            // ASSERT
            verify(wsPublisher).broadcastToTenant(event);
            verifyNoInteractions(notificationRepository, preferenceRepository, notificationEmailService, webhookService);
        }

        @Test
        void skipsActorUser_andStillBroadcasts_whenOnlyActorTargeted() {
            // ARRANGE
            event.setActorUserId("kc-u1");
            when(userRepository.findAllById(anyList())).thenReturn(List.of(user1));

            // ACT
            notificationService.dispatch(event);

            // ASSERT
            verify(wsPublisher).broadcastToTenant(event);
            verify(wsPublisher, never()).sendToUser(any(), any());
            verifyNoInteractions(notificationRepository, preferenceRepository, notificationEmailService, webhookService);
        }

        @Test
        void sendsDefaultChannels_andPersistsInApp_andDeliversWebhooks() {
            // ARRANGE
            when(userRepository.findAllById(anyList())).thenReturn(List.of(user1, user2));
            when(preferenceRepository.findByFkTenantIdAndCategoryIsNull("tenant-1")).thenReturn(List.of());
            when(preferenceRepository.findByFkTenantIdAndCategory("tenant-1", "INCIDENT")).thenReturn(List.of());
            when(notificationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            notificationService.dispatch(event);

            // ASSERT
            verify(wsPublisher, times(2)).sendToUser(eq(event), anyString());
            verify(notificationEmailService, times(2)).sendNotificationEmail(eq(event), any(User.class));
            verify(notificationRepository).saveAll(anyList());
            verify(wsPublisher).broadcastToTenant(event);
            verify(webhookService).deliverToWebhooks(event);

            ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationRepository).saveAll(captor.capture());
            assertNotNull(captor.getValue());
            assertEquals(2, captor.getValue().size());
            assertEquals("tenant-1", captor.getValue().get(0).getFkTenantId());
        }

        @Test
        void honorsMinSeverity_andDisablesAllChannels_whenEventBelowThreshold() {
            // ARRANGE
            when(userRepository.findAllById(anyList())).thenReturn(List.of(user1));

            NotificationPreference pref = NotificationPreference.builder()
                    .fkTenantId("tenant-1")
                    .fkUserId("u1")
                    .category(null)
                    .emailEnabled(true)
                    .websocketEnabled(true)
                    .inAppEnabled(true)
                    .webhookEnabled(true)
                    .minSeverity("CRITICAL")
                    .build();

            when(preferenceRepository.findByFkTenantIdAndCategoryIsNull("tenant-1")).thenReturn(List.of(pref));
            when(preferenceRepository.findByFkTenantIdAndCategory("tenant-1", "INCIDENT")).thenReturn(List.of());

            // ACT
            notificationService.dispatch(event); // severity=HIGH → rank=2, min=CRITICAL → rank=1, eventRank > minRank → empty

            // ASSERT
            verify(notificationRepository, never()).saveAll(anyList());
            verify(wsPublisher, never()).sendToUser(any(), any());
            verify(notificationEmailService, never()).sendNotificationEmail(any(), any());
            verify(wsPublisher).broadcastToTenant(event);
            verify(webhookService).deliverToWebhooks(event);
        }
    }

    @Nested
    @DisplayName("getUserNotifications")
    class GetUserNotifications {

        @Test
        void returnsMappedPage() {
            // ARRANGE
            Notification n = Notification.builder()
                    .pkNotificationId("n1")
                    .fkTenantId("tenant-1")
                    .fkUserId("u1")
                    .type("INCIDENT_CREATED")
                    .severity("HIGH")
                    .title("Incident")
                    .message("Msg")
                    .sourceService("events")
                    .isRead(false)
                    .createdAt(Instant.now())
                    .metadata(Map.of("k", "v"))
                    .build();

            Page<Notification> page = new PageImpl<>(List.of(n));
            when(notificationRepository.findByFkTenantIdAndFkUserIdOrderByCreatedAtDesc(eq("tenant-1"), eq("u1"), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<com.secufusion.tenant.dto.NotificationDTO> result =
                    notificationService.getUserNotifications("tenant-1", "u1", Pageable.unpaged());

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("n1", result.getContent().get(0).getNotificationId());
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCount {

        @Test
        void buildsMapsFromRepositoryRows() {
            // ARRANGE
            when(notificationRepository.countByFkTenantIdAndFkUserIdAndIsReadFalse("tenant-1", "u1")).thenReturn(3L);

            List<Object[]> byType = new ArrayList<>();
            byType.add(new Object[]{"INCIDENT_CREATED", 2L});
            when(notificationRepository.countUnreadByType("tenant-1", "u1")).thenReturn(byType);

            List<Object[]> bySeverity = new ArrayList<>();
            bySeverity.add(new Object[]{"HIGH", 1L});
            when(notificationRepository.countUnreadBySeverity("tenant-1", "u1")).thenReturn(bySeverity);

            // ACT
            UnreadCountDTO dto = notificationService.getUnreadCount("tenant-1", "u1");

            // ASSERT
            assertNotNull(dto);
            assertEquals(3L, dto.getTotalUnread());
            assertEquals(2L, dto.getByType().get("INCIDENT_CREATED"));
            assertEquals(1L, dto.getBySeverity().get("HIGH"));
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        void marksRead_whenTenantAndUserMatch() {
            // ARRANGE
            Notification n = Notification.builder()
                    .pkNotificationId("n1")
                    .fkTenantId("tenant-1")
                    .fkUserId("u1")
                    .type("INCIDENT_CREATED")
                    .severity("HIGH")
                    .title("Incident")
                    .message("Msg")
                    .sourceService("events")
                    .isRead(false)
                    .createdAt(Instant.now())
                    .build();

            when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            com.secufusion.tenant.dto.NotificationDTO dto = notificationService.markAsRead("tenant-1", "u1", "n1");

            // ASSERT
            assertNotNull(dto);
            assertTrue(dto.isRead());
            assertNotNull(dto.getReadAt());
        }

        @Test
        void throwsNotFound_whenTenantOrUserMismatch() {
            // ARRANGE
            Notification n = Notification.builder()
                    .pkNotificationId("n1")
                    .fkTenantId("tenant-1")
                    .fkUserId("u1")
                    .type("INCIDENT_CREATED")
                    .severity("HIGH")
                    .title("Incident")
                    .message("Msg")
                    .sourceService("events")
                    .isRead(false)
                    .createdAt(Instant.now())
                    .build();

            when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));

            // ACT + ASSERT
            assertThrows(ResourceNotFoundException.class,
                    () -> notificationService.markAsRead("tenant-2", "u1", "n1"));
        }
    }

    @Nested
    @DisplayName("markAllAsRead")
    class MarkAllAsRead {

        @Test
        void delegatesToRepository() {
            // ARRANGE
            when(notificationRepository.markAllAsRead(eq("tenant-1"), eq("u1"), any(Instant.class))).thenReturn(5);

            // ACT
            int updated = notificationService.markAllAsRead("tenant-1", "u1");

            // ASSERT
            assertEquals(5, updated);
            verify(notificationRepository).markAllAsRead(eq("tenant-1"), eq("u1"), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("preferences")
    class Preferences {

        @Test
        void updatePreferences_updatesExisting() {
            // ARRANGE
            NotificationPreference existing = NotificationPreference.builder()
                    .pkPreferenceId("p1")
                    .fkTenantId("tenant-1")
                    .fkUserId("u1")
                    .category("INCIDENT")
                    .emailEnabled(false)
                    .websocketEnabled(false)
                    .inAppEnabled(false)
                    .minSeverity(null)
                    .build();

            NotificationPreferenceDTO dto = NotificationPreferenceDTO.builder()
                    .preferenceId("p1")
                    .category("INCIDENT")
                    .emailEnabled(true)
                    .websocketEnabled(true)
                    .inAppEnabled(true)
                    .minSeverity("LOW")
                    .build();

            when(preferenceRepository.findByFkTenantIdAndFkUserIdAndCategory("tenant-1", "u1", "INCIDENT"))
                    .thenReturn(Optional.of(existing));
            when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            List<NotificationPreferenceDTO> result = notificationService.updatePreferences("tenant-1", "u1", List.of(dto));

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).isEmailEnabled());
            assertEquals("LOW", result.get(0).getMinSeverity());
        }
    }
}

