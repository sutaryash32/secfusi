package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.NotificationEvent;
import com.secufusion.tenant.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEmailServiceTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationEmailService notificationEmailService;

    private NotificationEvent event;
    private User user;

    @BeforeEach
    void setUp() {
        event = NotificationEvent.builder()
                .type("INCIDENT_CREATED")
                .tenantId("tenant-1")
                .severity("HIGH")
                .title("Phishing detected")
                .message("A phishing attempt was detected")
                .sourceService("events")
                .metadata(Map.of("incidentNumber", "INC-1"))
                .timestamp(Instant.now())
                .build();

        user = new User();
        user.setEmail("to@example.com");
    }

    @Nested
    @DisplayName("sendNotificationEmail")
    class SendNotificationEmail {

        @Test
        void callsEmailService_whenSendSucceeds() {
            // ARRANGE
            when(emailService.sendEmail(eq("tenant-1"), eq("to@example.com"), anyString(), anyString()))
                    .thenReturn(true);

            // ACT
            notificationEmailService.sendNotificationEmail(event, user);

            // ASSERT
            verify(emailService).sendEmail(eq("tenant-1"), eq("to@example.com"), anyString(), anyString());
        }

        @Test
        void swallowsExceptions_whenEmailServiceThrows() {
            // ARRANGE
            when(emailService.sendEmail(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("boom"));

            // ACT
            notificationEmailService.sendNotificationEmail(event, user);

            // ASSERT
            verify(emailService).sendEmail(eq("tenant-1"), eq("to@example.com"), anyString(), anyString());
        }

        @Test
        void stillCallsEmailService_whenTypeIsNull() {
            // ARRANGE
            event.setType(null);
            when(emailService.sendEmail(eq("tenant-1"), eq("to@example.com"), anyString(), anyString()))
                    .thenReturn(true);

            // ACT
            notificationEmailService.sendNotificationEmail(event, user);

            // ASSERT
            verify(emailService).sendEmail(eq("tenant-1"), eq("to@example.com"), anyString(), anyString());
        }
    }
}

