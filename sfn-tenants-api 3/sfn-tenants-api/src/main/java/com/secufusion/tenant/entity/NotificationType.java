package com.secufusion.tenant.entity;

public enum NotificationType {
    // Incident notifications (from events-api)
    INCIDENT_CREATED,
    INCIDENT_ASSIGNED,
    INCIDENT_STATUS_CHANGED,
    INCIDENT_PRIORITY_ESCALATED,
    INCIDENT_RESOLVED,
    INCIDENT_REOPENED,
    INCIDENT_COMMENT_ADDED,

    // Security notifications (from auth-api, future)
    LOGIN_FAILURE_THRESHOLD,
    ACCOUNT_LOCKED,
    SUSPICIOUS_LOGIN,

    // User management notifications (from iam-api, future)
    USER_CREATED,
    USER_DEACTIVATED,
    ROLE_CHANGED,

    // System notifications
    SYSTEM_ALERT
}
