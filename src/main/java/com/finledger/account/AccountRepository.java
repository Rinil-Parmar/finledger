package com.finledger.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    /**
     * Loads an account while taking a row-level write lock (SELECT ... FOR UPDATE).
     * Any other transaction trying to lock the same account waits until this one
     * commits, which serializes balance-changing operations on that account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.externalId = :externalId")
    Optional<Account> findByExternalIdForUpdate(@Param("externalId") String externalId);
}
