package com.secufusion.tenant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.tenant.dto.CreateWebhookConfigRequest;
import com.secufusion.tenant.dto.NotificationEvent;
import com.secufusion.tenant.dto.WebhookConfigDTO;
import com.secufusion.tenant.entity.WebhookConfig;
import com.secufusion.tenant.entity.WebhookDeliveryLog;
import com.secufusion.tenant.entity.WebhookType;
import com.secufusion.tenant.exception.ResourceConflictException;
import com.secufusion.tenant.repository.WebhookConfigRepository;
import com.secufusion.tenant.repository.WebhookDeliveryLogRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookConfigRepository webhookConfigRepository;
    @Mock
    private WebhookDeliveryLogRepository deliveryLogRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WebhookService webhookService;

    private CreateWebhookConfigRequest createReq;

    @BeforeEach
    void setUp() {
        createReq = CreateWebhookConfigRequest.builder()
                .name("hook1")
                .webhookType("CUSTOM")
                .url("https://example.com/hook")
                .headers(Map.of("X-Test", "1"))
                .secret("s")
                .categories(List.of("INCIDENT"))
                .severities(List.of("HIGH"))
                .build();
    }

    @Nested
    @DisplayName("createWebhook")
    class CreateWebhook {

        @Test
        void throwsConflict_whenNameExists() {
            // ARRANGE
            when(webhookConfigRepository.existsByFkTenantIdAndName("tenant-1", "hook1")).thenReturn(true);

            // ACT + ASSERT
            assertThrows(ResourceConflictException.class,
                    () -> webhookService.createWebhook("tenant-1", "u1", createReq));
            verify(webhookConfigRepository, never()).save(any());
        }

        @Test
        void savesAndReturnsDto() {
            // ARRANGE
            when(webhookConfigRepository.existsByFkTenantIdAndName("tenant-1", "hook1")).thenReturn(false);
            when(webhookConfigRepository.save(any(WebhookConfig.class))).thenAnswer(inv -> {
                WebhookConfig cfg = inv.getArgument(0);
                cfg.setPkWebhookConfigId("cfg-1");
                return cfg;
            });

            // ACT
            WebhookConfigDTO dto = webhookService.createWebhook("tenant-1", "u1", createReq);

            // ASSERT
            assertNotNull(dto);
            assertEquals("cfg-1", dto.getWebhookConfigId());
            assertEquals("hook1", dto.getName());
            assertEquals("CUSTOM", dto.getWebhookType());
            assertEquals("https://example.com/hook", dto.getUrl());
            assertTrue(dto.isHasSecret());
        }
    }

    @Nested
    @DisplayName("listWebhooks")
    class ListWebhooks {

        @Test
        void activeOnly_true_usesActiveQuery() {
            // ARRANGE
            WebhookConfig cfg = WebhookConfig.builder()
                    .pkWebhookConfigId("cfg-1")
                    .fkTenantId("tenant-1")
                    .name("hook1")
                    .webhookType(WebhookType.CUSTOM)
                    .url("u")
                    .isActive(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            when(webhookConfigRepository.findByFkTenantIdAndIsActiveTrueOrderByNameAsc("tenant-1"))
                    .thenReturn(List.of(cfg));

            // ACT
            List<WebhookConfigDTO> list = webhookService.listWebhooks("tenant-1", true);

            // ASSERT
            assertNotNull(list);
            assertEquals(1, list.size());
            verify(webhookConfigRepository).findByFkTenantIdAndIsActiveTrueOrderByNameAsc("tenant-1");
            verify(webhookConfigRepository, never()).findByFkTenantIdOrderByNameAsc(anyString());
        }

        @Test
        void activeOnly_false_usesAllQuery() {
            // ARRANGE
            when(webhookConfigRepository.findByFkTenantIdOrderByNameAsc("tenant-1"))
                    .thenReturn(List.of());

            // ACT
            List<WebhookConfigDTO> list = webhookService.listWebhooks("tenant-1", false);

            // ASSERT
            assertNotNull(list);
            assertEquals(0, list.size());
            verify(webhookConfigRepository).findByFkTenantIdOrderByNameAsc("tenant-1");
        }
    }

    @Nested
    @DisplayName("deliverToWebhooks")
    class DeliverToWebhooks {

        @Test
        void returns_whenNoActiveConfigs() {
            // ARRANGE
            NotificationEvent event = NotificationEvent.builder()
                    .tenantId("tenant-1")
                    .type("INCIDENT_CREATED")
                    .severity("HIGH")
                    .title("t")
                    .message("m")
                    .timestamp(Instant.now())
                    .build();
            when(webhookConfigRepository.findByFkTenantIdAndIsActiveTrueOrderByNameAsc("tenant-1"))
                    .thenReturn(List.of());

            // ACT
            webhookService.deliverToWebhooks(event);

            // ASSERT
            verifyNoInteractions(restTemplate);
            verify(deliveryLogRepository, never()).save(any());
        }

        @Test
        void delivers_andSavesLog_onSuccess() throws Exception {
            // ARRANGE
            NotificationEvent event = NotificationEvent.builder()
                    .tenantId("tenant-1")
                    .type("INCIDENT_CREATED")
                    .severity("HIGH")
                    .title("t")
                    .message("m")
                    .timestamp(Instant.now())
                    .build();

            WebhookConfig cfg = WebhookConfig.builder()
                    .pkWebhookConfigId("cfg-1")
                    .fkTenantId("tenant-1")
                    .name("hook1")
                    .webhookType(WebhookType.CUSTOM)
                    .url("https://example.com/hook")
                    .headers(Map.of("X-Test", "1"))
                    .categories(List.of("INCIDENT"))
                    .severities(List.of("HIGH"))
                    .isActive(true)
                    .build();

            when(webhookConfigRepository.findByFkTenantIdAndIsActiveTrueOrderByNameAsc("tenant-1"))
                    .thenReturn(List.of(cfg));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"x\":1}");
            when(restTemplate.exchange(eq("https://example.com/hook"), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            when(deliveryLogRepository.save(any(WebhookDeliveryLog.class))).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            webhookService.deliverToWebhooks(event);

            // ASSERT
            verify(restTemplate).exchange(eq("https://example.com/hook"), eq(HttpMethod.POST), any(), eq(String.class));
            ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
            verify(deliveryLogRepository, atLeastOnce()).save(captor.capture());
            assertNotNull(captor.getValue().getStatus());
        }
    }

    @Nested
    @DisplayName("getDeliveryLogs")
    class GetDeliveryLogs {

        @Test
        void usesConfigIdQuery_whenProvided() {
            // ARRANGE
            WebhookDeliveryLog log = WebhookDeliveryLog.builder()
                    .pkDeliveryLogId("l1")
                    .fkWebhookConfigId("cfg-1")
                    .fkTenantId("tenant-1")
                    .status("SUCCESS")
                    .attemptCount(1)
                    .createdAt(Instant.now())
                    .build();
            Page<WebhookDeliveryLog> page = new PageImpl<>(List.of(log));
            when(deliveryLogRepository.findByFkWebhookConfigIdOrderByCreatedAtDesc(eq("cfg-1"), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<com.secufusion.tenant.dto.WebhookDeliveryLogDTO> result =
                    webhookService.getDeliveryLogs("tenant-1", "cfg-1", Pageable.unpaged());

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("l1", result.getContent().get(0).getDeliveryLogId());
        }
    }
}

