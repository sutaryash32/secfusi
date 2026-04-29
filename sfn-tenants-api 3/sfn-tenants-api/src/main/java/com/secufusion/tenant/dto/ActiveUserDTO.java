package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a user who has one or more active sessions.
 * Returned as part of GET /api/tenants/sessions/users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveUserDTO {

    private String userId;

    private String username;

    /** IP address of the most recent session. */
    private String ipAddress;

    /** Total number of active sessions for this user. */
    private int sessionCount;

    /** Timestamp of the most recent activity across all sessions. */
    private LocalDateTime lastAccess;

    /** Timestamp when the user's earliest active session was created. */
    private LocalDateTime firstLogin;
}
