package com.secufusion.events.entity;

import com.secufusion.events.dto.EventDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_inbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventInboxEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId; // same UUID from Kafka

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String userName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", columnDefinition = "jsonb")
    private EventDto eventPayload;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private boolean processed = false;
}

