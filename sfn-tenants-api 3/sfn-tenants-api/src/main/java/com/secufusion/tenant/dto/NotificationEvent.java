package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationEvent {

    private String type;
    private String tenantId;
    private String severity;
    private String title;
    private String message;
    private String sourceService;
    private String sourceEntityId;
    private String sourceEntityType;
    private List<String> targetUserIds;
    private String targetRole;
    private String actorUserId;
    private List<String> channels;
    private Map<String, String> metadata;
    private Instant timestamp;
}
