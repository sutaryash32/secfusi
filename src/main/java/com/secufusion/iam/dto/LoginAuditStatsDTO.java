package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAuditStatsDTO {

    private long totalLoginAttempts;
    private long successfulLogins;
    private long failedLogins;
    private long uniqueActiveUsers;
    private long totalLogouts;
    private long passwordResets;
    private long accountLockouts;
    private double loginSuccessRate;

    // IAM-specific stats
    private long userCreations;
    private long userUpdates;
    private long userDeletions;
    private long roleAssignments;
    private long groupMembershipChanges;

    private Map<String, Long> eventCountsByType;
    private Map<String, Long> eventCountsBySourceService;
    private Map<Integer, Long> loginsByHour;

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}
