package com.secufusion.events.dto;


import com.secufusion.events.entity.EventsGroup;

/**
 * Projection interface for group membership statistics
 * Used by aggregate GROUP BY queries to return user counts per group
 */
public interface GroupMembershipStats {
    String getGroupId();
    String getGroupName();
    EventsGroup.GroupType getGroupType();
    Long getUserCount();
}
