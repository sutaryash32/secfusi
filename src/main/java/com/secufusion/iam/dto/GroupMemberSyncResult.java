package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result DTO for Azure AD group member sync.
 * Contains matched/unmatched info per group.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMemberSyncResult {

    private String groupId;
    private String groupName;
    private int azureMembersCount;
    private int matchedDeviceUsers;
    private int newMappingsCreated;
    private List<String> unmatchedEmails;
}
