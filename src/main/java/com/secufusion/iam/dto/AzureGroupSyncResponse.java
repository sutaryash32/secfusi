package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for Azure group synchronization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AzureGroupSyncResponse {

    private Integer totalFetched;
    private Integer newGroups;
    private Integer updatedGroups;
    private List<EventsGroupDto> syncedGroups;
    private Instant syncedAt;
    private String message;

    // Legacy field for backward compatibility
    @Deprecated
    private Integer syncedCount;

    // Legacy field for backward compatibility
    @Deprecated
    private List<EventsGroupDto> groups;
}
