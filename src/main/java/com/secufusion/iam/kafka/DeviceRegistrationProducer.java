package com.secufusion.iam.kafka;

import com.secufusion.iam.config.KafkaTopicConfig;
import com.secufusion.iam.dto.DeviceRegistrationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeviceRegistrationProducer {

    private final KafkaTemplate<String, DeviceRegistrationEvent> kafkaTemplate;

    public DeviceRegistrationProducer(KafkaTemplate<String, DeviceRegistrationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishDeviceRegistration(DeviceRegistrationEvent event) {
        if (event == null || event.getDeviceFingerprint() == null) {
            log.warn("Skipping device registration event: missing fingerprint");
            return;
        }

        kafkaTemplate.send(
                KafkaTopicConfig.DEVICE_REGISTRATION_TOPIC,
                event.getTenantId(),  // KEY = tenant for partition routing
                event
        ).whenComplete((SendResult<String, DeviceRegistrationEvent> result, Throwable ex) -> {
            if (ex != null) {
                log.error("Failed to publish device registration event: eventId={} tenant={} fingerprint={}",
                        event.getEventId(), event.getTenantId(), event.getDeviceFingerprint(), ex);
            } else {
                log.info("Published device registration: eventId={} tenant={} fingerprint={} partition={} offset={}",
                        event.getEventId(),
                        event.getTenantId(),
                        event.getDeviceFingerprint(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
