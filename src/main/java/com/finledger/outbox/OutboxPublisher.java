package com.finledger.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains PENDING outbox events to Kafka on a timer. Runs separately from the request
 * path: if Kafka is down, events stay PENDING and are retried on the next poll, giving
 * at-least-once delivery (consumers must be idempotent).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${finledger.outbox.topic}") String topic,
                           @Value("${finledger.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${finledger.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockPendingBatch(batchSize);
        for (OutboxEvent event : batch) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(topic, event.getAggregateId(), event.getPayload());
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record).get();   // block until the broker acknowledges
                repository.markPublished(event.getId());
            } catch (Exception ex) {
                // Leave the event PENDING so it is retried on the next poll.
                log.warn("Failed to publish outbox event {} ({}): {}",
                        event.getId(), event.getEventType(), ex.getMessage());
                repository.incrementAttempts(event.getId());
            }
        }
    }
}
