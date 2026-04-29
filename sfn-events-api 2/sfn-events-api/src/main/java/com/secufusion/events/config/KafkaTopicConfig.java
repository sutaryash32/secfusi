package com.secufusion.events.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.config.TopicBuilder;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicConfig {

    public static final String EVENTS_TOPIC = "quickstart-events";
    public static final String DEVICE_REGISTRATION_TOPIC = "device-registration";

    @Bean
    public NewTopic eventsTopic() {
        return TopicBuilder.name(EVENTS_TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic deviceRegistrationTopic() {
        return TopicBuilder.name(DEVICE_REGISTRATION_TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
