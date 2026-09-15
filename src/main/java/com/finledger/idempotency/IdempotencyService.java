package com.finledger.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.common.ConflictException;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes an operation idempotent under an Idempotency-Key.
 *
 * The key is claimed atomically in the database. The first caller to claim a key runs
 * the action exactly once and stores its response; any later call with the same key
 * gets the stored response back instead of running the action again. Because the claim
 * and the action share one transaction, concurrent duplicates are serialized by the DB.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StoredResponse process(String key, String requestHash, int successStatus, Supplier<Object> action) {
        int claimed = repository.tryClaim(key, requestHash);

        if (claimed == 0) {
            // The key was already used. Return the stored response (a safe replay).
            IdempotencyRecord existing = repository.findById(key)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record disappeared for key " + key));
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new ConflictException("Idempotency-Key was already used with a different request");
            }
            if (existing.getResponseBody() == null) {
                throw new ConflictException("A request with this Idempotency-Key is still being processed");
            }
            return new StoredResponse(existing.getResponseStatus(), existing.getResponseBody(), true);
        }

        // We own the key: run the action exactly once, then store its response.
        String body = serialize(action.get());
        repository.completeClaim(key, successStatus, body);
        return new StoredResponse(successStatus, body, false);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    public record StoredResponse(int status, String body, boolean replayed) {
    }
}
