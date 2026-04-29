package com.secufusion.tenant.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String POLICY_EVENTS_TOPIC = "policy-events";

    @Bean
    public NewTopic policyEventsTopic() {
        return TopicBuilder.name(POLICY_EVENTS_TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
