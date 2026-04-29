package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventKafkaMessage {
    private String eventId;      // UUID (MANDATORY)
    private String tenantId;
    private String userName;
    private EventDto event;

    private long occurredAt;
}

