package com.finledger.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByReference(String reference);

    /** True once a reversal has been posted for this journal (prevents double-reversal). */
    boolean existsByReversesJournalId(UUID reversesJournalId);
}
