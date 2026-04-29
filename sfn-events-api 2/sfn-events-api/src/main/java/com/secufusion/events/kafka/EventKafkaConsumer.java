package com.secufusion.events.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secufusion.events.config.KafkaTopicConfig;
import com.secufusion.events.dto.EventDto;
import com.secufusion.events.dto.EventKafkaMessage;
import com.secufusion.events.entity.EventInboxEntity;
import com.secufusion.events.repository.EventInboxRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
public class EventKafkaConsumer {

    private final EventInboxRepository inboxRepository;
    private final ObjectMapper objectMapper;

    public EventKafkaConsumer(EventInboxRepository inboxRepository,
                              ObjectMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("✅ EventKafkaConsumer initialized");
    }

    @KafkaListener(
            topics = KafkaTopicConfig.EVENTS_TOPIC,
            groupId = "event-consumer-group-v2",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(List<EventKafkaMessage> messages,
                        Acknowledgment ack) {
        log.info("🔥 Kafka consumer invoked");
        log.info("[KAFKA] Consumed batch size={}", messages.size());

        try {
            List<EventInboxEntity> inbox = messages.stream()
                    .filter(Objects::nonNull)
                    .map(msg -> new EventInboxEntity(
                            msg.getEventId(),
                            msg.getTenantId(),
                            msg.getUserName(),
                            msg.getEvent(),
                            LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(msg.getOccurredAt()),
                                    ZoneId.systemDefault()
                            ),
                            false
                    ))
                    .toList();

            inboxRepository.saveAll(inbox);

            ack.acknowledge(); // commit ONLY after DB success

        } catch (Exception ex) {
            log.error("[KAFKA] Failed to persist inbox batch", ex);
            throw ex; // Kafka retries
        }
    }

    private String toJson(EventDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
