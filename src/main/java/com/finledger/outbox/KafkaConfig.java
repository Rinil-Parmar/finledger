package com.finledger.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kafka wiring. Declares the events topic (KafkaAdmin auto-creates it on startup) and
 * enables scheduling so the outbox publisher can run on a timer.
 */
@Configuration
@EnableScheduling
public class KafkaConfig {

    @Bean
    public NewTopic finledgerEventsTopic(@Value("${finledger.outbox.topic}") String topic) {
        return new NewTopic(topic, 1, (short) 1);
    }
}
