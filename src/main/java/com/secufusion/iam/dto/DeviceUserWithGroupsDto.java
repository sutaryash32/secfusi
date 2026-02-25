package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Enhanced DTO for DeviceUser with group membership information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceUserWithGroupsDto {
    private String pkDeviceUserId;
    private String tenantId;
    private String email;
    private String userName;
    private String displayName;
    private String status; // ACTIVE, INACTIVE
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant createdAt;
    private Instant updatedAt;

    // Group membership information
    private Integer groupCount;
    private List<GroupMembershipInfo> groups;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupMembershipInfo {
        private String groupId;
        private String groupName;
        private String groupType; // APIKEY_GROUP, AZURE_GROUP
        private Boolean authorized;
        private Instant assignedAt;
        private String assignedBy;
    }
}
