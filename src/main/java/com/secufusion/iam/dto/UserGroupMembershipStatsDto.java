package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user group membership statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGroupMembershipStatsDto {
    private String deviceUserId;
    private String email;
    private String displayName;
    private String source; // APIKEY, AZURE
    private Long groupCount;
}
