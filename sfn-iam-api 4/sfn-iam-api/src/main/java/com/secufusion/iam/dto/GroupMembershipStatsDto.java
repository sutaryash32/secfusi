package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for group membership statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMembershipStatsDto {
    private String groupId;
    private String groupName;
    private String groupType; // APIKEY_GROUP, AZURE_GROUP
    private Long userCount;
    private Boolean authorized;
}
