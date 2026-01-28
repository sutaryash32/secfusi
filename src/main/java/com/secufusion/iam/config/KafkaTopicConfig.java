package com.secufusion.iam.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String DEVICE_REGISTRATION_TOPIC = "device-registration";

    @Bean
    public NewTopic deviceRegistrationTopic() {
        return TopicBuilder.name(DEVICE_REGISTRATION_TOPIC)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
