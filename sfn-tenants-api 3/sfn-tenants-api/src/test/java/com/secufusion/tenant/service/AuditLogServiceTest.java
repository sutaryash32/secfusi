package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.AuditLog;
import com.secufusion.tenant.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        auditLog = AuditLog.builder()
                .id(1L)
                .entityName("BrowserPolicy")
                .entityId("bp-1")
                .operation("UPDATE")
                .oldData("{\"x\":1}")
                .newData("{\"x\":2}")
                .tenantId("tenant-1")
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("getEntityHistory")
    class GetEntityHistory {

        @Test
        void returnsLogsFromRepository() {
            // ARRANGE
            when(auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("BrowserPolicy", "bp-1"))
                    .thenReturn(List.of(auditLog));

            // ACT
            List<AuditLog> result = auditLogService.getEntityHistory("BrowserPolicy", "bp-1");

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("BrowserPolicy", result.get(0).getEntityName());
            verify(auditLogRepository).findByEntityNameAndEntityIdOrderByCreatedAtDesc("BrowserPolicy", "bp-1");
        }
    }

    @Nested
    @DisplayName("getAuditLogsByTenant")
    class GetAuditLogsByTenant {

        @Test
        void returnsPageFromRepository() {
            // ARRANGE
            Page<AuditLog> page = new PageImpl<>(List.of(auditLog));
            when(auditLogRepository.findByTenantId(eq("tenant-1"), any(Pageable.class))).thenReturn(page);

            // ACT
            Page<AuditLog> result = auditLogService.getAuditLogsByTenant("tenant-1", 0, 10);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(auditLogRepository).findByTenantId(eq("tenant-1"), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("getAuditLogsByEntityType")
    class GetAuditLogsByEntityType {

        @Test
        void returnsLogs() {
            // ARRANGE
            when(auditLogRepository.findByEntityNameOrderByCreatedAtDesc("BrowserPolicy"))
                    .thenReturn(List.of(auditLog));

            // ACT
            List<AuditLog> result = auditLogService.getAuditLogsByEntityType("BrowserPolicy");

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(auditLogRepository).findByEntityNameOrderByCreatedAtDesc("BrowserPolicy");
        }
    }

    @Nested
    @DisplayName("getAuditLogsByEntityTypeAndTenant")
    class GetAuditLogsByEntityTypeAndTenant {

        @Test
        void returnsLogs() {
            // ARRANGE
            when(auditLogRepository.findByEntityNameAndTenantIdOrderByCreatedAtDesc("BrowserPolicy", "tenant-1"))
                    .thenReturn(List.of(auditLog));

            // ACT
            List<AuditLog> result = auditLogService.getAuditLogsByEntityTypeAndTenant("BrowserPolicy", "tenant-1");

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(auditLogRepository).findByEntityNameAndTenantIdOrderByCreatedAtDesc("BrowserPolicy", "tenant-1");
        }
    }

    @Nested
    @DisplayName("getAuditLogsByDateRange")
    class GetAuditLogsByDateRange {

        @Test
        void returnsLogs() {
            // ARRANGE
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now();
            when(auditLogRepository.findByDateRange(start, end)).thenReturn(List.of(auditLog));

            // ACT
            List<AuditLog> result = auditLogService.getAuditLogsByDateRange(start, end);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(auditLogRepository).findByDateRange(start, end);
        }
    }

    @Nested
    @DisplayName("getEntityHistoryByDateRange")
    class GetEntityHistoryByDateRange {

        @Test
        void returnsLogs() {
            // ARRANGE
            LocalDateTime start = LocalDateTime.now().minusDays(7);
            LocalDateTime end = LocalDateTime.now();
            when(auditLogRepository.findByEntityAndDateRange("BrowserPolicy", "bp-1", start, end))
                    .thenReturn(List.of(auditLog));

            // ACT
            List<AuditLog> result = auditLogService.getEntityHistoryByDateRange("BrowserPolicy", "bp-1", start, end);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(auditLogRepository).findByEntityAndDateRange("BrowserPolicy", "bp-1", start, end);
        }
    }

    @Nested
    @DisplayName("getAuditLogsByTenantAndDateRange")
    class GetAuditLogsByTenantAndDateRange {

        @Test
        void returnsPage() {
            // ARRANGE
            LocalDateTime start = LocalDateTime.now().minusHours(2);
            LocalDateTime end = LocalDateTime.now();
            Page<AuditLog> page = new PageImpl<>(List.of(auditLog));
            when(auditLogRepository.findByTenantIdAndDateRange(eq("tenant-1"), eq(start), eq(end), any(Pageable.class)))
                    .thenReturn(page);

            // ACT
            Page<AuditLog> result = auditLogService.getAuditLogsByTenantAndDateRange("tenant-1", start, end, 0, 5);

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(auditLogRepository).findByTenantIdAndDateRange(eq("tenant-1"), eq(start), eq(end), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("getAuditLogsByOperation")
    class GetAuditLogsByOperation {

        @Test
        void returnsLogs() {
            // ARRANGE
            when(auditLogRepository.findByOperationOrderByCreatedAtDesc("UPDATE")).thenReturn(List.of(auditLog));

            // ACT
            List<AuditLog> result = auditLogService.getAuditLogsByOperation("UPDATE");

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(auditLogRepository).findByOperationOrderByCreatedAtDesc("UPDATE");
        }
    }

    @Nested
    @DisplayName("getAuditLogsByUser")
    class GetAuditLogsByUser {

        @Test
        void returnsLogs() {
            // ARRANGE
            when(auditLogRepository.findByCreatedByOrderByCreatedAtDesc("admin")).thenReturn(List.of(auditLog));

            // ACT
            List<AuditLog> result = auditLogService.getAuditLogsByUser("admin");

            // ASSERT
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(auditLogRepository).findByCreatedByOrderByCreatedAtDesc("admin");
        }
    }
}

