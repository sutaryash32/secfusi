package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for EventsGroupDeviceUserMapping entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceUserGroupMappingDto {

    private String mappingId;
    private String deviceUserId;
    private String deviceUserEmail;
    private String groupId;
    private String groupName;
    private Instant assignedAt;
    private String assignedBy;
}
