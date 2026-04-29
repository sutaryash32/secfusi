package com.secufusion.events.service;

import com.secufusion.events.entity.SmtpConfig;
import com.secufusion.events.entity.User;
import com.secufusion.events.event.IncidentNotificationEvent;
import com.secufusion.events.repository.SmtpConfigRepository;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEmailServiceTest {

    @Mock
    private SmtpConfigRepository smtpConfigRepository;

    private NotificationEmailService service;

    private MockedStatic<Transport> transportMock;
    private MockedConstruction<MimeMessage> mimeMessageMock;

    private static final String TENANT_ID = "tenant-1";
    private static final String FROM_EMAIL = "noreply@secufusion.com";
    private static final String TO_EMAIL = "user@example.com";
    private User testUser;

    @BeforeEach
    void setUp() {
        // Create service with constructor injection
        service = new NotificationEmailService(smtpConfigRepository);

        // Set default @Value fields (application.properties fallback)
        ReflectionTestUtils.setField(service, "defaultSmtpHost", "smtp.example.com");
        ReflectionTestUtils.setField(service, "defaultSmtpPort", 587);
        ReflectionTestUtils.setField(service, "defaultSmtpAuth", "true");
        ReflectionTestUtils.setField(service, "defaultSmtpStarttls", "true");
        ReflectionTestUtils.setField(service, "defaultSmtpUsername", "user");
        ReflectionTestUtils.setField(service, "defaultSmtpPassword", "pass");
        ReflectionTestUtils.setField(service, "defaultFromEmail", FROM_EMAIL);

        // Mock static Transport.send()
        transportMock = mockStatic(Transport.class);
        transportMock.when(() -> Transport.send(any(MimeMessage.class))).then(inv -> null);

        // Mock MimeMessage construction
        mimeMessageMock = mockConstruction(MimeMessage.class,
                (mock, context) -> {
                    // do nothing; we just need to avoid NPE
                });

        testUser = new User();
        testUser.setEmail(TO_EMAIL);
    }

    @AfterEach
    void tearDown() {
        transportMock.close();
        mimeMessageMock.close();
    }

    // Helper: create a basic IncidentNotificationEvent
    private IncidentNotificationEvent createEvent(String type) {
        return new IncidentNotificationEvent(
                type,
                TENANT_ID,
                null,                           // actorUserId
                "inc-123",
                "INC-000001",
                "P2_HIGH",
                "MALWARE",
                "HIGH",
                "Test Incident Notification",
                "This is a test message",
                null,
                null
        );
    }

    // Helper: create a SmtpConfig for tenant
    private SmtpConfig createTenantConfig() {
        SmtpConfig config = new SmtpConfig();
        config.setHost("mail.tenant.com");
        config.setPort(587);
        config.setAuth("true");
        config.setStarttls("true");
        config.setUsername("tenant-user");
        config.setPassword("tenant-pass");
        config.setFromEmail("tenant@tenant.com");
        config.setFromName("Tenant SecuFusion");
        return config;
    }

    @Nested
    @DisplayName("sendNotificationEmail - Happy Path")
    class HappyPathTests {

        @Test
        @DisplayName("Tenant-specific SMTP config used")
        void tenantSpecificConfig() {
            // ARRANGE
            SmtpConfig config = createTenantConfig();
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(config));

            IncidentNotificationEvent event = createEvent("INCIDENT_CREATED");

            // ACT
            service.sendNotificationEmail(event, testUser);

            // ASSERT
            // Verify Transport.send was called (email sent)
            transportMock.verify(() -> Transport.send(any(MimeMessage.class)), times(1));

            // Verify the correct repository method was called
            verify(smtpConfigRepository).findByFkTenantIdAndIsActiveTrue(TENANT_ID);
        }

        @Test
        @DisplayName("Fallback to default DB config (fk_tenant_id IS NULL)")
        void fallbackToDefaultDbConfig() {
            // ARRANGE
            // no tenant config
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());
            SmtpConfig defaultConfig = new SmtpConfig();
            defaultConfig.setHost("default.example.com");
            defaultConfig.setPort(587);
            defaultConfig.setAuth("true");
            defaultConfig.setStarttls("true");
            defaultConfig.setUsername("default-user");
            defaultConfig.setPassword("default-pass");
            defaultConfig.setFromEmail("default@secufusion.com");
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.of(defaultConfig));

            IncidentNotificationEvent event = createEvent("INCIDENT_ASSIGNED");

            // ACT
            service.sendNotificationEmail(event, testUser);

            // ASSERT
            transportMock.verify(() -> Transport.send(any(MimeMessage.class)), times(1));
            verify(smtpConfigRepository).findByFkTenantIdIsNullAndIsActiveTrue();
        }

        @Test
        @DisplayName("Fallback to application.properties when no DB config")
        void fallbackToApplicationProperties() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(anyString()))
                    .thenReturn(Optional.empty());
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.empty());

            IncidentNotificationEvent event = createEvent("INCIDENT_RESOLVED");

            // ACT
            service.sendNotificationEmail(event, testUser);

            // ASSERT
            transportMock.verify(() -> Transport.send(any(MimeMessage.class)), times(1));
        }
    }

    @Nested
    @DisplayName("sendNotificationEmail - Sad Path")
    class SadPathTests {

        @Test
        @DisplayName("No SMTP config – email skipped")
        void noSmtpConfig_skipEmail() {
            // ARRANGE
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(anyString()))
                    .thenReturn(Optional.empty());
            when(smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue())
                    .thenReturn(Optional.empty());
            // clear default host to simulate no application.properties fallback
            ReflectionTestUtils.setField(service, "defaultSmtpHost", "");

            IncidentNotificationEvent event = createEvent("INCIDENT_CREATED");

            // ACT
            service.sendNotificationEmail(event, testUser);

            // ASSERT
            // Transport.send should never be called
            transportMock.verify(() -> Transport.send(any(MimeMessage.class)), never());
        }

        @Test
        @DisplayName("Transport.send throws exception – logs error, does not rethrow")
        void transportSendThrowsException_logsAndSwallows() {
            // ARRANGE
            SmtpConfig config = createTenantConfig();
            when(smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(config));

            // configure Transport.send to throw
            transportMock.when(() -> Transport.send(any(MimeMessage.class)))
                    .thenThrow(new MessagingException("SMTP server down"));

            IncidentNotificationEvent event = createEvent("INCIDENT_CREATED");

            // ACT & ASSERT – no exception propagates
            assertDoesNotThrow(() -> service.sendNotificationEmail(event, testUser));

            // Verify Transport.send was called (and threw)
            transportMock.verify(() -> Transport.send(any(MimeMessage.class)), times(1));
        }
    }
}