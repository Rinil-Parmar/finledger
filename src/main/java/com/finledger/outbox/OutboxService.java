package com.finledger.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Appends events to the outbox. Callers invoke {@link #append} from inside their own
 * business transaction, so the event and the business change commit together (or not
 * at all). This method never opens its own transaction on purpose.
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void append(String aggregateType, String aggregateId, String eventType, Object payload) {
        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, serialize(payload)));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
