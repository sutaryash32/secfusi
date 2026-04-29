//package com.secufusion.tenant.util;
//
//import com.secufusion.tenant.config.KafkaTopicConfig;
//import com.secufusion.tenant.dto.PolicyChangeEvent;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.support.Acknowledgment;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Slf4j
//@Service
//public class PolicyKafkaConsumer {
//
//    private final PolicyWsPublisher wsPublisher;
//
//    public PolicyKafkaConsumer(PolicyWsPublisher wsPublisher) {
//        this.wsPublisher = wsPublisher;
//    }
//
//    @KafkaListener(
//            topics = KafkaTopicConfig.POLICY_EVENTS_TOPIC,
//            groupId = "policy-ws-group"
//    )
//    public void consume(
//            List<PolicyChangeEvent> events,
//            Acknowledgment ack
//    ) {
//        for (PolicyChangeEvent event : events) {
//            log.info("✅ Policy event received: {}", event.getEventType());
//            wsPublisher.publish(event);
//        }
//        ack.acknowledge(); // 🔥 REQUIRED
//    }
//}
//
