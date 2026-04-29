package com.secufusion.events.kafka;

import com.secufusion.events.config.KafkaTopicConfig;
import com.secufusion.events.dto.EventKafkaMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class EventKafkaProducer {

    private final KafkaTemplate<String, EventKafkaMessage> kafkaTemplate;

    public EventKafkaProducer(KafkaTemplate<String, EventKafkaMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(EventKafkaMessage message) {

        kafkaTemplate.send(
                KafkaTopicConfig.EVENTS_TOPIC,
                message.getTenantId(),   // KEY = tenant
                message
        ).whenComplete((SendResult<String, EventKafkaMessage> res, Throwable ex) -> {

            if (ex != null) {
                log.error(
                        "❌ Kafka send failed eventId={} tenant={}",
                        message.getEventId(),
                        message.getTenantId(),
                        ex
                );
            } else {
                log.info(
                        "✅ Kafka sent eventId={} tenant={} partition={} offset={}",
                        message.getEventId(),
                        message.getTenantId(),
                        res.getRecordMetadata().partition(),
                        res.getRecordMetadata().offset()
                );
            }
        });
    }
}