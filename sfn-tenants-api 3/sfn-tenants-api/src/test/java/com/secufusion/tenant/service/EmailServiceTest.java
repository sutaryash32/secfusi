package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.SmtpConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SmtpConfigService smtpConfigService;

    @InjectMocks
    private EmailService emailService;

    private SmtpConfig smtpConfig;

    @BeforeEach
    void setUp() {
        smtpConfig = new SmtpConfig();
        smtpConfig.setHost("smtp.example.com");
        smtpConfig.setPort(587);
        smtpConfig.setAuth("true");
        smtpConfig.setStarttls("true");
        smtpConfig.setSsl("false");
        smtpConfig.setUsername("user");
        smtpConfig.setPassword("pass");
        smtpConfig.setFromEmail("noreply@example.com");
        smtpConfig.setFromName("SecuFusion");
    }

    @Nested
    @DisplayName("sendEmail")
    class SendEmail {

        @Test
        void returnsTrue_whenTransportSendSucceeds() {
            // ARRANGE
            when(smtpConfigService.getSmtpConfigForTenant("tenant-1")).thenReturn(smtpConfig);
            try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
                transport.when(() -> Transport.send(any())).thenAnswer(inv -> null);

                // ACT
                boolean ok = emailService.sendEmail("tenant-1", "to@example.com", "Sub", "<b>Hi</b>");

                // ASSERT
                assertTrue(ok);
                verify(smtpConfigService).getSmtpConfigForTenant("tenant-1");
                transport.verify(() -> Transport.send(any()), times(1));
            }
        }

        @Test
        void returnsFalse_whenTransportSendThrows() {
            // ARRANGE
            when(smtpConfigService.getSmtpConfigForTenant("tenant-1")).thenReturn(smtpConfig);
            try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
                transport.when(() -> Transport.send(any())).thenThrow(new MessagingException("fail"));

                // ACT
                boolean ok = emailService.sendEmail("tenant-1", "to@example.com", "Sub", "<b>Hi</b>");

                // ASSERT
                assertFalse(ok);
                transport.verify(() -> Transport.send(any()), times(1));
            }
        }
    }

    @Nested
    @DisplayName("sendEmailWithDefaultConfig")
    class SendEmailWithDefaultConfig {

        @Test
        void usesDefaultConfig() {
            // ARRANGE
            when(smtpConfigService.getDefaultSmtpConfig()).thenReturn(smtpConfig);
            try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
                transport.when(() -> Transport.send(any())).thenAnswer(inv -> null);

                // ACT
                boolean ok = emailService.sendEmailWithDefaultConfig("to@example.com", "Sub", "<b>Hi</b>");

                // ASSERT
                assertTrue(ok);
                verify(smtpConfigService).getDefaultSmtpConfig();
                transport.verify(() -> Transport.send(any()), times(1));
            }
        }
    }

    @Nested
    @DisplayName("sendEmailToMultiple")
    class SendEmailToMultiple {

        @Test
        void returnsTrue_whenSendSucceeds() {
            // ARRANGE
            when(smtpConfigService.getSmtpConfigForTenant("tenant-1")).thenReturn(smtpConfig);
            try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
                transport.when(() -> Transport.send(any())).thenAnswer(inv -> null);

                // ACT
                boolean ok = emailService.sendEmailToMultiple("tenant-1",
                        new String[]{"a@example.com", "b@example.com"}, "Sub", "<b>Hi</b>");

                // ASSERT
                assertTrue(ok);
                transport.verify(() -> Transport.send(any()), times(1));
            }
        }
    }

    @Nested
    @DisplayName("templates")
    class Templates {

        @Test
        void sendWelcomeEmail_delegatesToSendEmail() {
            // ARRANGE
            when(smtpConfigService.getSmtpConfigForTenant("tenant-1")).thenReturn(smtpConfig);
            try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
                transport.when(() -> Transport.send(any())).thenAnswer(inv -> null);

                // ACT
                boolean ok = emailService.sendWelcomeEmail("tenant-1", "to@example.com", "Bob", "Acme");

                // ASSERT
                assertTrue(ok);
                verify(smtpConfigService).getSmtpConfigForTenant("tenant-1");
                transport.verify(() -> Transport.send(any()), times(1));
            }
        }

        @Test
        void sendPasswordResetEmail_delegatesToSendEmail() {
            // ARRANGE
            when(smtpConfigService.getSmtpConfigForTenant("tenant-1")).thenReturn(smtpConfig);
            try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
                transport.when(() -> Transport.send(any())).thenAnswer(inv -> null);

                // ACT
                boolean ok = emailService.sendPasswordResetEmail("tenant-1", "to@example.com", "Bob", "https://reset");

                // ASSERT
                assertTrue(ok);
                verify(smtpConfigService).getSmtpConfigForTenant("tenant-1");
                transport.verify(() -> Transport.send(any()), times(1));
            }
        }
    }
}

