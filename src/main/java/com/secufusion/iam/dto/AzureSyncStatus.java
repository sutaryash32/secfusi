package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for tracking Azure group sync status
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AzureSyncStatus {

    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    private String tenantId;
    private Status status;
    private Integer totalFetched;
    private Integer newGroups;
    private Integer updatedGroups;
    private Instant startedAt;
    private Instant completedAt;
    private String message;
    private String errorMessage;
}
