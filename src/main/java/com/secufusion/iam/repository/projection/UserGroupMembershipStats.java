package com.secufusion.iam.repository.projection;

/**
 * Projection interface for user group membership statistics
 * Used by aggregate GROUP BY queries to return group counts per user
 */
public interface UserGroupMembershipStats {
    String getDeviceUserId();
    String getEmail();
    String getDisplayName();
    String getUserName();
    String getSource();
    Long getGroupCount();
}
