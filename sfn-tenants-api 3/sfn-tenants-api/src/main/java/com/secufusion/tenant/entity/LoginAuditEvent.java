package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a login/authentication audit event.
 * Tracks authentication-related events like login, logout, failed attempts, etc.
 */
@Entity
@Table(name = "login_audit_event", indexes = {
        @Index(name = "idx_login_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_login_audit_user", columnList = "user_id"),
        @Index(name = "idx_login_audit_username", columnList = "username"),
        @Index(name = "idx_login_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_login_audit_timestamp", columnList = "event_timestamp"),
        @Index(name = "idx_login_audit_ip", columnList = "ip_address")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "realm_name", length = 100)
    private String realmName;

    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private LoginEventType eventType;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "auth_method", length = 50)
    private String authMethod;

    @Column(name = "mfa_used")
    private Boolean mfaUsed;

    @Column(name = "remember_me")
    private Boolean rememberMe;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "device_info", length = 200)
    private String deviceInfo;

    @Column(name = "additional_details", columnDefinition = "TEXT")
    private String additionalDetails;

    @Column(name = "source_service", length = 50)
    @Enumerated(EnumType.STRING)
    private SourceService sourceService;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (eventTimestamp == null) {
            eventTimestamp = LocalDateTime.now();
        }
    }

    /**
     * Enum representing source microservice for unified audit logging.
     */
    public enum SourceService {
        TENANTS_API,
        IAM_API,
        GATEWAY_API,
        NOTIFICATION_API,
        EXTENSION, REPORTING_API
    }

    /**
     * Enum representing different types of login/auth events.
     */
    public enum LoginEventType {
        // Authentication Events
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        LOGOUT_ALL,
        SESSION_REVOKED,
        TOKEN_REFRESH,
        TOKEN_REFRESH_FAILURE,
        PASSWORD_RESET_REQUEST,
        PASSWORD_RESET_SUCCESS,
        PASSWORD_CHANGE,
        MFA_CHALLENGE,
        MFA_SUCCESS,
        MFA_FAILURE,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        ACCOUNT_DISABLED,
        ACCOUNT_ENABLED,
        IMPERSONATION_START,
        IMPERSONATION_END,
        CONSENT_GRANTED,
        CONSENT_REVOKED,
        IDENTITY_PROVIDER_LOGIN,
        IDENTITY_PROVIDER_LINK,
        IDENTITY_PROVIDER_UNLINK,
        REGISTER,
        REGISTER_ERROR,
        VERIFY_EMAIL,
        UPDATE_EMAIL,
        UPDATE_PROFILE,
        CLIENT_LOGIN,
        CODE_TO_TOKEN,
        CODE_TO_TOKEN_ERROR,

        // IAM User Management Events
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        USER_ENABLED,
        USER_DISABLED,
        USER_EMAIL_VERIFIED,
        USER_PASSWORD_SET,
        USER_ATTRIBUTES_UPDATED,

        // IAM Role Management Events
        ROLE_CREATED,
        ROLE_UPDATED,
        ROLE_DELETED,
        ROLE_ASSIGNED_TO_USER,
        ROLE_REMOVED_FROM_USER,
        ROLE_PERMISSIONS_UPDATED,

        // IAM Group Management Events
        GROUP_CREATED,
        GROUP_UPDATED,
        GROUP_DELETED,
        USER_ADDED_TO_GROUP,
        USER_REMOVED_FROM_GROUP,
        ROLE_ASSIGNED_TO_GROUP,
        ROLE_REMOVED_FROM_GROUP,

        // IAM Scope Management Events
        SCOPE_CREATED,
        SCOPE_UPDATED,
        SCOPE_DELETED,

        // IAM Client/Application Events
        CLIENT_CREATED,
        CLIENT_UPDATED,
        CLIENT_DELETED,
        CLIENT_SECRET_ROTATED,
        CLIENT_SCOPE_ASSIGNED,
        CLIENT_SCOPE_REMOVED,

        // IAM Permission Events
        PERMISSION_CREATED,
        PERMISSION_UPDATED,
        PERMISSION_DELETED,
        PERMISSION_ASSIGNED,
        PERMISSION_REVOKED,

        // IAM Policy Events
        POLICY_CREATED,
        POLICY_UPDATED,
        POLICY_DELETED,

        // Tenant Management Events
        TENANT_CREATED,
        TENANT_UPDATED,
        TENANT_DELETED,
        TENANT_SUSPENDED,
        TENANT_ACTIVATED,
        TENANT_SETTINGS_UPDATED
    }
}
