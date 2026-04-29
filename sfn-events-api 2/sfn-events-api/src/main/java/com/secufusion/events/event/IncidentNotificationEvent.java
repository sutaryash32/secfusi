package com.secufusion.events.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Spring Application Event published when an incident state change needs a Kafka notification.
 *
 * This is NOT the Kafka event itself — it is a plain Spring event that is consumed by
 * {@link IncidentNotificationListener} after the DB transaction commits, so that:
 *   1. The DB connection is released before any Kafka I/O begins.
 *   2. Kafka is never called if the transaction rolls back.
 *
 * All data is copied from the Incident entity at publish time (still inside the transaction)
 * so the listener does not need to touch the DB.
 */
@Getter
@RequiredArgsConstructor
public class IncidentNotificationEvent {

    private final String type;
    private final String tenantId;
    private final String actorUserId;
    private final String incidentId;
    private final String incidentNumber;
    private final String priority;
    private final String category;
    private final String severity;
    private final String notificationTitle;
    private final String message;
    private final List<String> targetUserIds;
    private final Map<String, String> metadata;
}
