package com.finledger.outbox;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints to observe the outbox pipeline over HTTP (useful for Postman and
 * ops). Kafka itself is not HTTP-reachable, so this surfaces what the pipeline did:
 * outbox rows and their status, plus what the consumer has received.
 */
@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxController {

    private final OutboxRepository repository;
    private final EventConsumer consumer;

    public OutboxController(OutboxRepository repository, EventConsumer consumer) {
        this.repository = repository;
        this.consumer = consumer;
    }

    /** Recent outbox events, or the events for one aggregate id (e.g. a transfer reference). */
    @GetMapping("/events")
    public List<OutboxEventView> events(@RequestParam(required = false) String aggregateId) {
        List<OutboxEvent> events = (aggregateId != null && !aggregateId.isBlank())
                ? repository.findByAggregateId(aggregateId)
                : repository.findTop50ByOrderByCreatedAtDesc();
        return events.stream().map(OutboxEventView::from).toList();
    }

    /** Events this instance's Kafka consumer has received (proves end-to-end delivery). */
    @GetMapping("/consumed")
    public List<String> consumed() {
        return consumer.getReceived();
    }

    public record OutboxEventView(String id, String aggregateType, String aggregateId,
                                  String eventType, String status, int attempts) {
        static OutboxEventView from(OutboxEvent e) {
            return new OutboxEventView(e.getId().toString(), e.getAggregateType(),
                    e.getAggregateId(), e.getEventType(), e.getStatus().name(), e.getAttempts());
        }
    }
}
