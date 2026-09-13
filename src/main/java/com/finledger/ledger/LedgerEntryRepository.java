package com.finledger.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByJournalId(UUID journalId);

    List<LedgerEntry> findByLedgerAccountIdOrderByPostedAtAsc(UUID ledgerAccountId);

    /**
     * Derives a ledger account's balance by summing its entries: credits add, debits
     * subtract. This is correct for a LIABILITY account (e.g. a customer wallet), whose
     * balance grows on the credit side. Returns 0 when there are no entries.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN e.direction = com.finledger.ledger.EntryDirection.CREDIT
                                     THEN e.amount ELSE -e.amount END), 0)
            FROM LedgerEntry e
            WHERE e.ledgerAccountId = :ledgerAccountId
            """)
    BigDecimal deriveCreditPositiveBalance(@Param("ledgerAccountId") UUID ledgerAccountId);
}
