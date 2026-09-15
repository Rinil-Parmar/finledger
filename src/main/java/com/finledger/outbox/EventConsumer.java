package com.finledger.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes published events from Kafka. In a real system this would send notifications,
 * feed analytics, or trigger reconciliation. Here it logs each event and records what it
 * received so tests can prove end-to-end delivery. It must be idempotent, since delivery
 * is at-least-once.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final List<String> received = new CopyOnWriteArrayList<>();

    @KafkaListener(topics = "${finledger.outbox.topic}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, "eventType");
        log.info("Consumed event type={} key={} payload={}", eventType, record.key(), record.value());
        received.add(eventType + ":" + record.key());
    }

    /** What this consumer has received so far (used by tests). */
    public List<String> getReceived() {
        return received;
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String key) {
        var header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
