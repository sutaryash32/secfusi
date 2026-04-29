package com.secufusion.events.dto;

/**
 * Projection interface for user group membership statistics
 * Used by aggregate GROUP BY queries to return group counts per user
 */
public interface UserGroupMembershipStats {
    String getDeviceUserId();
    String getEmail();
    String getDisplayName();
    String getSource();
    Long getGroupCount();
}
