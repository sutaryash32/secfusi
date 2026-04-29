package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationDTO {

    private String notificationId;
    private String type;
    private String severity;
    private String title;
    private String message;
    private String sourceService;
    private String sourceEntityId;
    private String sourceEntityType;
    private boolean isRead;
    private String readAt;
    private String createdAt;
    private Map<String, String> metadata;
}
