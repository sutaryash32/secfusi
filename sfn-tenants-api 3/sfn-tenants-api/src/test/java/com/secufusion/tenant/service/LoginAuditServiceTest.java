package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.LoginAuditEventDTO;
import com.secufusion.tenant.dto.LoginAuditPageResponse;
import com.secufusion.tenant.dto.LoginAuditSearchRequest;
import com.secufusion.tenant.dto.LoginAuditStatsDTO;
import com.secufusion.tenant.entity.LoginAuditEvent;
import com.secufusion.tenant.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.tenant.entity.LoginAuditEvent.SourceService;
import com.secufusion.tenant.repository.LoginAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAuditServiceTest {

    @Mock
    private LoginAuditRepository loginAuditRepository;

    @InjectMocks
    private LoginAuditService loginAuditService;

    private static final String TENANT_ID = "tenant-123";
    private static final String REALM = "realm-1";
    private static final String USER_ID = "user-456";
    private static final String USERNAME = "testuser";
    private static final String EMAIL = "test@example.com";
    private static final String IP = "192.168.1.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String CLIENT_ID = "client-1";
    private static final String SESSION_ID = "session-abc";
    private static final LocalDateTime START = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2024, 1, 31, 23, 59);

    private LoginAuditEvent mockEvent;
    private Page<LoginAuditEvent> mockPage;

    @BeforeEach
    void setUp() {
        mockEvent = LoginAuditEvent.builder()
                .tenantId(TENANT_ID)
                .realmName(REALM)
                .userId(USER_ID)
                .username(USERNAME)
                .email(EMAIL)
                .eventType(LoginEventType.LOGIN_SUCCESS)
                .eventTimestamp(LocalDateTime.now())
                .ipAddress(IP)
                .userAgent(USER_AGENT)
                .clientId(CLIENT_ID)
                .sessionId(SESSION_ID)
                .success(true)
                .build();

        List<LoginAuditEvent> eventList = List.of(mockEvent);
        mockPage = new PageImpl<>(eventList, PageRequest.of(0, 20), 1);
    }

    // ============================================================
    // logEventAsync
    // ============================================================

    @Test
    void logEventAsync_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(mockEvent)).thenReturn(mockEvent);

        // ACT
        loginAuditService.logEventAsync(mockEvent);

        // ASSERT
        verify(loginAuditRepository).save(mockEvent);
    }

    @Test
    void logEventAsync_Exception_ShouldCatchAndNotPropagate() {
        // ARRANGE
        doThrow(new RuntimeException("DB error")).when(loginAuditRepository).save(mockEvent);

        // ACT & ASSERT - should not throw
        assertDoesNotThrow(() -> loginAuditService.logEventAsync(mockEvent));
        verify(loginAuditRepository).save(mockEvent);
    }

    // ============================================================
    // logEvent
    // ============================================================

    @Test
    void logEvent_HappyPath_ShouldReturnSavedEvent() {
        // ARRANGE
        when(loginAuditRepository.save(mockEvent)).thenReturn(mockEvent);

        // ACT
        LoginAuditEvent result = loginAuditService.logEvent(mockEvent);

        // ASSERT
        assertNotNull(result);
        assertEquals(TENANT_ID, result.getTenantId());
        assertEquals(USERNAME, result.getUsername());
        verify(loginAuditRepository).save(mockEvent);
    }

    // ============================================================
    // logLoginSuccess
    // ============================================================

    @Test
    void logLoginSuccess_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logLoginSuccess(TENANT_ID, REALM, USER_ID, USERNAME, EMAIL,
                IP, USER_AGENT, CLIENT_ID, SESSION_ID, "password", true, false);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logLoginFailure
    // ============================================================

    @Test
    void logLoginFailure_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logLoginFailure(TENANT_ID, REALM, USERNAME, IP, USER_AGENT,
                CLIENT_ID, "401", "Invalid credentials");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logLogout
    // ============================================================

    @Test
    void logLogout_NormalLogout_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logLogout(TENANT_ID, REALM, USER_ID, USERNAME, IP, SESSION_ID, false);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    @Test
    void logLogout_LogoutAll_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logLogout(TENANT_ID, REALM, USER_ID, USERNAME, IP, SESSION_ID, true);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logLogoutFailure
    // ============================================================

    @Test
    void logLogoutFailure_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logLogoutFailure(TENANT_ID, REALM, USER_ID, USERNAME, SESSION_ID, "Session expired");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logSessionRevoked
    // ============================================================

    @Test
    void logSessionRevoked_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logSessionRevoked(TENANT_ID, REALM, USER_ID, USERNAME, SESSION_ID, "admin");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logAccountLocked
    // ============================================================

    @Test
    void logAccountLocked_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logAccountLocked(TENANT_ID, REALM, USER_ID, USERNAME, IP, "Too many attempts");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logPasswordResetRequest
    // ============================================================

    @Test
    void logPasswordResetRequest_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logPasswordResetRequest(TENANT_ID, REALM, USER_ID, USERNAME, EMAIL, IP);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logPasswordChange (6 params)
    // ============================================================

    @Test
    void logPasswordChange_SixParams_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logPasswordChange(TENANT_ID, REALM, USER_ID, USERNAME, IP);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logPasswordChange (with changedBy)
    // ============================================================

    @Test
    void logPasswordChange_WithChangedBy_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logPasswordChange(TENANT_ID, REALM, USER_ID, "admin");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logPasswordReset
    // ============================================================

    @Test
    void logPasswordReset_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logPasswordReset(TENANT_ID, REALM, USER_ID, "admin");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logBulkSessionRevoke
    // ============================================================

    @Test
    void logBulkSessionRevoke_AllSucceeded_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logBulkSessionRevoke(TENANT_ID, REALM, 10, 0, "admin");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    @Test
    void logBulkSessionRevoke_WithFailures_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logBulkSessionRevoke(TENANT_ID, REALM, 8, 2, "admin");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logUnifiedEvent
    // ============================================================

    @Test
    void logUnifiedEvent_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUnifiedEvent(TENANT_ID, REALM, LoginEventType.USER_CREATED,
                SourceService.IAM_API, USER_ID, USERNAME, "Test details");

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logUserCreated
    // ============================================================

    @Test
    void logUserCreated_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUserCreated(TENANT_ID, REALM, USER_ID, USERNAME, EMAIL, "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logUserUpdated
    // ============================================================

    @Test
    void logUserUpdated_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUserUpdated(TENANT_ID, REALM, USER_ID, USERNAME, "admin", "email changed", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logUserDeleted
    // ============================================================

    @Test
    void logUserDeleted_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUserDeleted(TENANT_ID, REALM, USER_ID, USERNAME, "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logUserStatusChanged
    // ============================================================

    @Test
    void logUserStatusChanged_Enabled_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUserStatusChanged(TENANT_ID, REALM, USER_ID, USERNAME, true, "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    @Test
    void logUserStatusChanged_Disabled_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUserStatusChanged(TENANT_ID, REALM, USER_ID, USERNAME, false, "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logRoleCreated
    // ============================================================

    @Test
    void logRoleCreated_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logRoleCreated(TENANT_ID, REALM, "role-1", "ADMIN_ROLE", "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logRoleAssignedToUser
    // ============================================================

    @Test
    void logRoleAssignedToUser_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logRoleAssignedToUser(TENANT_ID, REALM, USER_ID, USERNAME, "ADMIN_ROLE", "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logRoleRemovedFromUser
    // ============================================================

    @Test
    void logRoleRemovedFromUser_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logRoleRemovedFromUser(TENANT_ID, REALM, USER_ID, USERNAME, "ADMIN_ROLE", "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logGroupCreated
    // ============================================================

    @Test
    void logGroupCreated_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logGroupCreated(TENANT_ID, REALM, "group-1", "ADMIN_GROUP", "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logUserAddedToGroup
    // ============================================================

    @Test
    void logUserAddedToGroup_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUserAddedToGroup(TENANT_ID, REALM, USER_ID, USERNAME, "ADMIN_GROUP", "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logUserRemovedFromGroup
    // ============================================================

    @Test
    void logUserRemovedFromGroup_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logUserRemovedFromGroup(TENANT_ID, REALM, USER_ID, USERNAME, "ADMIN_GROUP", "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logTenantCreated
    // ============================================================

    @Test
    void logTenantCreated_HappyPath_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logTenantCreated(TENANT_ID, "MyTenant", "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // logTenantStatusChanged
    // ============================================================

    @Test
    void logTenantStatusChanged_Activated_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logTenantStatusChanged(TENANT_ID, "MyTenant", true, "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    @Test
    void logTenantStatusChanged_Suspended_ShouldSaveEvent() {
        // ARRANGE
        when(loginAuditRepository.save(any(LoginAuditEvent.class))).thenReturn(mockEvent);

        // ACT
        loginAuditService.logTenantStatusChanged(TENANT_ID, "MyTenant", false, "admin", SourceService.IAM_API);

        // ASSERT
        verify(loginAuditRepository).save(any(LoginAuditEvent.class));
    }

    // ============================================================
    // getEvents
    // ============================================================

    @Test
    void getEvents_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdOrderByEventTimestampDesc(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEvents(TENANT_ID, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdOrderByEventTimestampDesc(eq(TENANT_ID), any(Pageable.class));
    }

    @Test
    void getEvents_EmptyPage_ShouldReturnEmptyResponse() {
        // ARRANGE
        Page<LoginAuditEvent> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(loginAuditRepository.findByTenantIdOrderByEventTimestampDesc(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(emptyPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEvents(TENANT_ID, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdOrderByEventTimestampDesc(eq(TENANT_ID), any(Pageable.class));
    }

    // ============================================================
    // getEventsByUser
    // ============================================================

    @Test
    void getEventsByUser_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdAndUserIdOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USER_ID), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEventsByUser(TENANT_ID, USER_ID, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndUserIdOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USER_ID), any(Pageable.class));
    }

    // ============================================================
    // getEventsByUsername
    // ============================================================

    @Test
    void getEventsByUsername_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdAndUsernameOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USERNAME), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEventsByUsername(TENANT_ID, USERNAME, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndUsernameOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USERNAME), any(Pageable.class));
    }

    // ============================================================
    // getEventsByType
    // ============================================================

    @Test
    void getEventsByType_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdAndEventTypeOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(LoginEventType.LOGIN_FAILURE), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEventsByType(TENANT_ID, LoginEventType.LOGIN_FAILURE, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndEventTypeOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(LoginEventType.LOGIN_FAILURE), any(Pageable.class));
    }

    // ============================================================
    // getEventsByIpAddress
    // ============================================================

    @Test
    void getEventsByIpAddress_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdAndIpAddressOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(IP), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEventsByIpAddress(TENANT_ID, IP, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndIpAddressOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(IP), any(Pageable.class));
    }

    // ============================================================
    // getEventsByTimeRange
    // ============================================================

    @Test
    void getEventsByTimeRange_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdAndEventTimestampBetweenOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(START), eq(END), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEventsByTimeRange(TENANT_ID, START, END, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndEventTimestampBetweenOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(START), eq(END), any(Pageable.class));
    }

    // ============================================================
    // searchEvents
    // ============================================================

    @Test
    void searchEvents_ByUserId_ShouldDelegateToGetEventsByUser() {
        // ARRANGE
        LoginAuditSearchRequest request = new LoginAuditSearchRequest();
        request.setUserId(USER_ID);
        request.setPage(0);
        request.setSize(20);

        when(loginAuditRepository.findByTenantIdAndUserIdOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USER_ID), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.searchEvents(TENANT_ID, request);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndUserIdOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USER_ID), any(Pageable.class));
    }

    @Test
    void searchEvents_ByUsername_ShouldDelegateToGetEventsByUsername() {
        // ARRANGE
        LoginAuditSearchRequest request = new LoginAuditSearchRequest();
        request.setUsername(USERNAME);
        request.setPage(0);
        request.setSize(20);

        when(loginAuditRepository.findByTenantIdAndUsernameOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USERNAME), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.searchEvents(TENANT_ID, request);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndUsernameOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(USERNAME), any(Pageable.class));
    }

    @Test
    void searchEvents_ByEventType_ShouldDelegateToGetEventsByType() {
        // ARRANGE
        LoginAuditSearchRequest request = new LoginAuditSearchRequest();
        request.setEventType(LoginEventType.LOGIN_FAILURE);
        request.setPage(0);
        request.setSize(20);

        when(loginAuditRepository.findByTenantIdAndEventTypeOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(LoginEventType.LOGIN_FAILURE), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.searchEvents(TENANT_ID, request);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndEventTypeOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(LoginEventType.LOGIN_FAILURE), any(Pageable.class));
    }

    @Test
    void searchEvents_ByIpAddress_ShouldDelegateToGetEventsByIpAddress() {
        // ARRANGE
        LoginAuditSearchRequest request = new LoginAuditSearchRequest();
        request.setIpAddress(IP);
        request.setPage(0);
        request.setSize(20);

        when(loginAuditRepository.findByTenantIdAndIpAddressOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(IP), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.searchEvents(TENANT_ID, request);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndIpAddressOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(IP), any(Pageable.class));
    }

    @Test
    void searchEvents_ByTimeRange_ShouldDelegateToGetEventsByTimeRange() {
        // ARRANGE
        LoginAuditSearchRequest request = new LoginAuditSearchRequest();
        request.setStartTime(START);
        request.setEndTime(END);
        request.setPage(0);
        request.setSize(20);

        when(loginAuditRepository.findByTenantIdAndEventTimestampBetweenOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(START), eq(END), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.searchEvents(TENANT_ID, request);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndEventTimestampBetweenOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(START), eq(END), any(Pageable.class));
    }

    @Test
    void searchEvents_NoFilters_ShouldDelegateToGetEvents() {
        // ARRANGE
        LoginAuditSearchRequest request = new LoginAuditSearchRequest();
        request.setPage(0);
        request.setSize(20);

        when(loginAuditRepository.findByTenantIdOrderByEventTimestampDesc(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.searchEvents(TENANT_ID, request);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdOrderByEventTimestampDesc(eq(TENANT_ID), any(Pageable.class));
    }

    // ============================================================
    // getStats
    // ============================================================

    @Test
    void getStats_HappyPath_ShouldReturnStatsDTO() {
        // ARRANGE
        List<Object[]> eventCounts = new ArrayList<>();
        eventCounts.add(new Object[]{LoginEventType.LOGIN_SUCCESS, 100L});
        eventCounts.add(new Object[]{LoginEventType.LOGIN_FAILURE, 20L});
        eventCounts.add(new Object[]{LoginEventType.LOGOUT, 50L});
        eventCounts.add(new Object[]{LoginEventType.LOGOUT_ALL, 10L});
        eventCounts.add(new Object[]{LoginEventType.PASSWORD_RESET_REQUEST, 5L});
        eventCounts.add(new Object[]{LoginEventType.PASSWORD_RESET_SUCCESS, 3L});
        eventCounts.add(new Object[]{LoginEventType.ACCOUNT_LOCKED, 2L});
        when(loginAuditRepository.getEventCountsByType(TENANT_ID, START, END)).thenReturn(eventCounts);

        when(loginAuditRepository.countUniqueActiveUsers(TENANT_ID, START, END)).thenReturn(30L);

        List<Object[]> hourlyData = new ArrayList<>();
        hourlyData.add(new Object[]{9, 15L});
        hourlyData.add(new Object[]{14, 45L});
        when(loginAuditRepository.getLoginsByHour(TENANT_ID, START, END)).thenReturn(hourlyData);

        // ACT
        LoginAuditStatsDTO result = loginAuditService.getStats(TENANT_ID, START, END);

        // ASSERT
        assertNotNull(result);
        assertEquals(120L, result.getTotalLoginAttempts());
        assertEquals(100L, result.getSuccessfulLogins());
        assertEquals(20L, result.getFailedLogins());
        assertEquals(30L, result.getUniqueActiveUsers());
        assertEquals(60L, result.getTotalLogouts());
        assertEquals(8L, result.getPasswordResets());
        assertEquals(2L, result.getAccountLockouts());
        assertEquals(83.33, result.getLoginSuccessRate());
        assertNotNull(result.getEventCountsByType());
        assertNotNull(result.getLoginsByHour());
        assertEquals(2, result.getLoginsByHour().size());
        assertEquals(START, result.getPeriodStart());
        assertEquals(END, result.getPeriodEnd());
        verify(loginAuditRepository).getEventCountsByType(TENANT_ID, START, END);
        verify(loginAuditRepository).countUniqueActiveUsers(TENANT_ID, START, END);
        verify(loginAuditRepository).getLoginsByHour(TENANT_ID, START, END);
    }

    @Test
    void getStats_NoEvents_ShouldReturnZeroStats() {
        // ARRANGE
        when(loginAuditRepository.getEventCountsByType(TENANT_ID, START, END))
                .thenReturn(new ArrayList<>());
        when(loginAuditRepository.countUniqueActiveUsers(TENANT_ID, START, END)).thenReturn(0L);
        when(loginAuditRepository.getLoginsByHour(TENANT_ID, START, END))
                .thenReturn(new ArrayList<>());

        // ACT
        LoginAuditStatsDTO result = loginAuditService.getStats(TENANT_ID, START, END);

        // ASSERT
        assertNotNull(result);
        assertEquals(0L, result.getTotalLoginAttempts());
        assertEquals(0L, result.getSuccessfulLogins());
        assertEquals(0L, result.getFailedLogins());
        assertEquals(0.0, result.getLoginSuccessRate());
        verify(loginAuditRepository).getEventCountsByType(TENANT_ID, START, END);
    }

    @Test
    void getStats_DefaultSwitchCase_ShouldIgnoreUnknownEventType() {
        // ARRANGE - includes an event type not handled by the switch (falls into default)
        List<Object[]> eventCounts = new ArrayList<>();
        eventCounts.add(new Object[]{LoginEventType.LOGIN_SUCCESS, 50L});
        eventCounts.add(new Object[]{LoginEventType.USER_CREATED, 5L}); // default branch
        when(loginAuditRepository.getEventCountsByType(TENANT_ID, START, END)).thenReturn(eventCounts);
        when(loginAuditRepository.countUniqueActiveUsers(TENANT_ID, START, END)).thenReturn(10L);
        when(loginAuditRepository.getLoginsByHour(TENANT_ID, START, END))
                .thenReturn(new ArrayList<>());

        // ACT
        LoginAuditStatsDTO result = loginAuditService.getStats(TENANT_ID, START, END);

        // ASSERT
        assertNotNull(result);
        assertEquals(50L, result.getTotalLoginAttempts());
        assertEquals(50L, result.getSuccessfulLogins());
        assertEquals(0L, result.getFailedLogins());
        verify(loginAuditRepository).getEventCountsByType(TENANT_ID, START, END);
    }

    // ============================================================
    // countRecentLoginFailures
    // ============================================================

    @Test
    void countRecentLoginFailures_HappyPath_ShouldReturnCount() {
        // ARRANGE
        when(loginAuditRepository.countLoginFailuresSince(eq(TENANT_ID), eq(USERNAME), any(LocalDateTime.class)))
                .thenReturn(5L);

        // ACT
        long result = loginAuditService.countRecentLoginFailures(TENANT_ID, USERNAME, 15);

        // ASSERT
        assertEquals(5L, result);
        verify(loginAuditRepository).countLoginFailuresSince(eq(TENANT_ID), eq(USERNAME), any(LocalDateTime.class));
    }

    // ============================================================
    // countRecentLoginFailuresFromIp
    // ============================================================

    @Test
    void countRecentLoginFailuresFromIp_HappyPath_ShouldReturnCount() {
        // ARRANGE
        when(loginAuditRepository.countLoginFailuresFromIpSince(eq(TENANT_ID), eq(IP), any(LocalDateTime.class)))
                .thenReturn(3L);

        // ACT
        long result = loginAuditService.countRecentLoginFailuresFromIp(TENANT_ID, IP, 30);

        // ASSERT
        assertEquals(3L, result);
        verify(loginAuditRepository).countLoginFailuresFromIpSince(eq(TENANT_ID), eq(IP), any(LocalDateTime.class));
    }

    // ============================================================
    // findSuspiciousIps
    // ============================================================

    @Test
    void findSuspiciousIps_HappyPath_ShouldReturnListOfMaps() {
        // ARRANGE
        List<Object[]> results = new ArrayList<>();
        results.add(new Object[]{"10.0.0.1", 25L});
        results.add(new Object[]{"10.0.0.2", 50L});
        when(loginAuditRepository.findSuspiciousIps(eq(TENANT_ID), any(LocalDateTime.class), eq(10L)))
                .thenReturn(results);

        // ACT
        List<Map<String, Object>> result = loginAuditService.findSuspiciousIps(TENANT_ID, 5, 10L);

        // ASSERT
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("10.0.0.1", result.get(0).get("ipAddress"));
        assertEquals(25L, result.get(0).get("failedAttempts"));
        assertEquals("10.0.0.2", result.get(1).get("ipAddress"));
        assertEquals(50L, result.get(1).get("failedAttempts"));
        verify(loginAuditRepository).findSuspiciousIps(eq(TENANT_ID), any(LocalDateTime.class), eq(10L));
    }

    @Test
    void findSuspiciousIps_EmptyResults_ShouldReturnEmptyList() {
        // ARRANGE
        when(loginAuditRepository.findSuspiciousIps(eq(TENANT_ID), any(LocalDateTime.class), eq(10L)))
                .thenReturn(new ArrayList<>());

        // ACT
        List<Map<String, Object>> result = loginAuditService.findSuspiciousIps(TENANT_ID, 5, 10L);

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ============================================================
    // getSessionEvents
    // ============================================================

    @Test
    void getSessionEvents_HappyPath_ShouldReturnDTOList() {
        // ARRANGE
        List<LoginAuditEvent> events = List.of(mockEvent);
        when(loginAuditRepository.findByTenantIdAndSessionIdOrderByEventTimestampDesc(TENANT_ID, SESSION_ID))
                .thenReturn(events);

        // ACT
        List<LoginAuditEventDTO> result = loginAuditService.getSessionEvents(TENANT_ID, SESSION_ID);

        // ASSERT
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(loginAuditRepository).findByTenantIdAndSessionIdOrderByEventTimestampDesc(TENANT_ID, SESSION_ID);
    }

    @Test
    void getSessionEvents_EmptyList_ShouldReturnEmptyList() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdAndSessionIdOrderByEventTimestampDesc(TENANT_ID, SESSION_ID))
                .thenReturn(Collections.emptyList());

        // ACT
        List<LoginAuditEventDTO> result = loginAuditService.getSessionEvents(TENANT_ID, SESSION_ID);

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ============================================================
    // getEventsBySourceService
    // ============================================================

    @Test
    void getEventsBySourceService_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findByTenantIdAndSourceServiceOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(SourceService.IAM_API), any(Pageable.class))).thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getEventsBySourceService(TENANT_ID, SourceService.IAM_API, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findByTenantIdAndSourceServiceOrderByEventTimestampDesc(
                eq(TENANT_ID), eq(SourceService.IAM_API), any(Pageable.class));
    }

    // ============================================================
    // getUserManagementEvents
    // ============================================================

    @Test
    void getUserManagementEvents_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findUserManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class)))
                .thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getUserManagementEvents(TENANT_ID, START, END, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findUserManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class));
    }

    // ============================================================
    // getRoleManagementEvents
    // ============================================================

    @Test
    void getRoleManagementEvents_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findRoleManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class)))
                .thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getRoleManagementEvents(TENANT_ID, START, END, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findRoleManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class));
    }

    // ============================================================
    // getGroupManagementEvents
    // ============================================================

    @Test
    void getGroupManagementEvents_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findGroupManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class)))
                .thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getGroupManagementEvents(TENANT_ID, START, END, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findGroupManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class));
    }

    // ============================================================
    // getTenantManagementEvents
    // ============================================================

    @Test
    void getTenantManagementEvents_HappyPath_ShouldReturnPageResponse() {
        // ARRANGE
        when(loginAuditRepository.findTenantManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class)))
                .thenReturn(mockPage);

        // ACT
        LoginAuditPageResponse result = loginAuditService.getTenantManagementEvents(TENANT_ID, START, END, 0, 20);

        // ASSERT
        assertNotNull(result);
        verify(loginAuditRepository).findTenantManagementEvents(eq(TENANT_ID), eq(START), eq(END), any(Pageable.class));
    }

    // ============================================================
    // getEventCountsBySourceService
    // ============================================================

    @Test
    void getEventCountsBySourceService_HappyPath_ShouldReturnCountsMap() {
        // ARRANGE
        List<Object[]> results = new ArrayList<>();
        results.add(new Object[]{SourceService.IAM_API, 50L});
        results.add(new Object[]{SourceService.EXTENSION, 30L});
        when(loginAuditRepository.getEventCountsBySourceService(TENANT_ID, START, END)).thenReturn(results);

        // ACT
        Map<String, Long> result = loginAuditService.getEventCountsBySourceService(TENANT_ID, START, END);

        // ASSERT
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(50L, result.get("IAM_API"));
        assertEquals(30L, result.get("EXTENSION"));
        verify(loginAuditRepository).getEventCountsBySourceService(TENANT_ID, START, END);
    }

    @Test
    void getEventCountsBySourceService_WithNullService_ShouldSkipNull() {
        // ARRANGE
        List<Object[]> results = new ArrayList<>();
        results.add(new Object[]{SourceService.IAM_API, 50L});
        results.add(new Object[]{null, 10L});
        when(loginAuditRepository.getEventCountsBySourceService(TENANT_ID, START, END)).thenReturn(results);

        // ACT
        Map<String, Long> result = loginAuditService.getEventCountsBySourceService(TENANT_ID, START, END);

        // ASSERT
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(50L, result.get("IAM_API"));
        verify(loginAuditRepository).getEventCountsBySourceService(TENANT_ID, START, END);
    }

    @Test
    void getEventCountsBySourceService_EmptyResults_ShouldReturnEmptyMap() {
        // ARRANGE
        when(loginAuditRepository.getEventCountsBySourceService(TENANT_ID, START, END))
                .thenReturn(new ArrayList<>());

        // ACT
        Map<String, Long> result = loginAuditService.getEventCountsBySourceService(TENANT_ID, START, END);

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ============================================================
    // deleteOldEvents
    // ============================================================

    @Test
    void deleteOldEvents_HappyPath_ShouldCallRepository() {
        // ARRANGE
        doNothing().when(loginAuditRepository).deleteByTenantIdAndEventTimestampBefore(
                eq(TENANT_ID), any(LocalDateTime.class));

        // ACT
        loginAuditService.deleteOldEvents(TENANT_ID, 90);

        // ASSERT
        verify(loginAuditRepository).deleteByTenantIdAndEventTimestampBefore(
                eq(TENANT_ID), any(LocalDateTime.class));
    }
}