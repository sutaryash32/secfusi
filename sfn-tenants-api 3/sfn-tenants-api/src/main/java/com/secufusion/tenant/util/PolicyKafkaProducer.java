package com.secufusion.tenant.util;

import com.secufusion.tenant.config.KafkaTopicConfig;
import com.secufusion.tenant.dto.PolicyChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PolicyKafkaProducer {

    private final KafkaTemplate<String, PolicyChangeEvent> kafkaTemplate;

    public PolicyKafkaProducer(KafkaTemplate<String, PolicyChangeEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    public void send(PolicyChangeEvent event) {
        try {
            kafkaTemplate.send(
                    KafkaTopicConfig.POLICY_EVENTS_TOPIC,
                    event.getTenantId(),   // key = tenant
                    event
            );
            log.info("Policy event sent: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Failed to send policy event to Kafka: {}", event.getEventType(), e);
        }
    }
}

