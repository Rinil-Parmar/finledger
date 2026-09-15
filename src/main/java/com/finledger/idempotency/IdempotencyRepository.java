package com.finledger.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Atomically claims a key. Returns 1 if this call inserted the row (the caller now
     * owns the key), or 0 if the key already existed. Concurrent callers with the same
     * key block here until the first transaction commits, so only one owns the key.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO idempotency_records (idempotency_key, request_hash) "
            + "VALUES (:key, :hash) ON CONFLICT DO NOTHING", nativeQuery = true)
    int tryClaim(@Param("key") String key, @Param("hash") String hash);

    /** Stores the response once the owned work has succeeded. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE idempotency_records SET response_status = :status, response_body = :body "
            + "WHERE idempotency_key = :key", nativeQuery = true)
    void completeClaim(@Param("key") String key, @Param("status") int status, @Param("body") String body);
}
