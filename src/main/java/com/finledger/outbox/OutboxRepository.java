package com.finledger.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateId(String aggregateId);

    long countByEventType(String eventType);

    /**
     * Fetches a batch of unpublished events, oldest first, locking each row so no other
     * publisher instance can grab it. SKIP LOCKED means concurrent publishers each take a
     * different batch instead of blocking on each other.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' "
            + "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE outbox_events SET status = 'PUBLISHED', published_at = NOW() WHERE id = :id",
            nativeQuery = true)
    void markPublished(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE outbox_events SET attempts = attempts + 1 WHERE id = :id", nativeQuery = true)
    void incrementAttempts(@Param("id") UUID id);
}
