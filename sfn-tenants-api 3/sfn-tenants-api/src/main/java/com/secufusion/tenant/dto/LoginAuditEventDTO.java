package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.LoginAuditEvent;
import com.secufusion.tenant.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.tenant.entity.LoginAuditEvent.SourceService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAuditEventDTO {

    private Long id;
    private String tenantId;
    private String realmName;
    private String userId;
    private String username;
    private String email;
    private LoginEventType eventType;
    private LocalDateTime eventTimestamp;
    private String ipAddress;
    private String userAgent;
    private String clientId;
    private String sessionId;
    private boolean success;
    private String errorMessage;
    private String errorCode;
    private String authMethod;
    private Boolean mfaUsed;
    private Boolean rememberMe;
    private String location;
    private String deviceInfo;
    private String additionalDetails;
    private SourceService sourceService;

    public static LoginAuditEventDTO fromEntity(LoginAuditEvent entity) {
        return LoginAuditEventDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .realmName(entity.getRealmName())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .eventType(entity.getEventType())
                .eventTimestamp(entity.getEventTimestamp())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .clientId(entity.getClientId())
                .sessionId(entity.getSessionId())
                .success(entity.isSuccess())
                .errorMessage(entity.getErrorMessage())
                .errorCode(entity.getErrorCode())
                .authMethod(entity.getAuthMethod())
                .mfaUsed(entity.getMfaUsed())
                .rememberMe(entity.getRememberMe())
                .location(entity.getLocation())
                .deviceInfo(entity.getDeviceInfo())
                .additionalDetails(entity.getAdditionalDetails())
                .sourceService(entity.getSourceService())
                .build();
    }
}
