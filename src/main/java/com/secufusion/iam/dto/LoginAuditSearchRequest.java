package com.secufusion.iam.dto;

import com.secufusion.iam.entity.LoginAuditEvent.LoginEventType;
import com.secufusion.iam.entity.LoginAuditEvent.SourceService;
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
    private SourceService sourceService;
    private String ipAddress;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 50;
}
