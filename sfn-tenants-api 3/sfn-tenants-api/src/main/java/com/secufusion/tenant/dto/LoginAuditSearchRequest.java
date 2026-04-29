package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.LoginAuditEvent.LoginEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAuditSearchRequest {

    private String userId;
    private String username;
    private LoginEventType eventType;
    private String ipAddress;
    private String sessionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean successOnly;
    private Boolean failuresOnly;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 50;
}
