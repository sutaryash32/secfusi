package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for EventsGroup entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventsGroupDto {

    private String pkEventsGroupId;
    private String tenantId;
    private String name;
    private String description;
    private String groupType; // "APIKEY_GROUP" or "AZURE_GROUP"
    private Boolean authorized;
    private Boolean extensionAuthorized;
    private String azureGroupId;
    private String azureGroupDisplayName;
    private Instant syncedAt;
    private Boolean isDefault;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    public String getDescription() {
        if ("APIKEY_GROUP".equalsIgnoreCase(this.groupType)
                && Boolean.TRUE.equals(this.isDefault)) {
            return "Auto-created default group for Tenant key users";
        }
        return this.description;
    }
}
