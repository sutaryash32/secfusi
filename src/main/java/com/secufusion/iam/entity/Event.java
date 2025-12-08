package com.secufusion.iam.entity;

import com.secufusion.iam.dto.EventDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "pk_event_id", nullable = false, updatable = false)
    private String pkEventId;

    @Column(name = "url", nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "time_stamp", nullable = false, columnDefinition = "timestamptz")
    private LocalDateTime timeStamp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    private String userName;

    public static Event from(EventDto eventDto, Tenant tenant, String userName) {
        return Event.builder()
                .url(eventDto.getUrl())
                .timeStamp(LocalDateTime.parse(eventDto.getTimeStamp()))
                .tenant(tenant)
                .userName(userName)
                .build();
    }

    public static EventDto toDto(Event event) {
        EventDto dto = new EventDto();
        dto.setUrl(event.getUrl());
        dto.setTimeStamp(String.valueOf(event.getTimeStamp()));
        return dto;
    }
}
