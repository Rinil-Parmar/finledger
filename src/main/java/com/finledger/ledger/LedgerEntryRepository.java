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

    /**
     * Every movement of the pooled CASH account, with its journal reference. These are the
     * deposits, withdrawals, and reversals that correspond to real bank settlements, which
     * reconciliation compares against the external institution's records.
     */
    @Query(value = """
            SELECT j.reference AS reference, le.direction AS direction,
                   le.amount AS amount, le.currency AS currency
            FROM ledger_entries le
            JOIN journal_entries j ON le.journal_id = j.id
            JOIN ledger_accounts la ON le.ledger_account_id = la.id
            WHERE la.code = 'CASH'
            """, nativeQuery = true)
    List<CashMovementView> findCashMovements();

    /** All ledger lines for one account, oldest first, with their journal reference (for the UI). */
    @Query(value = """
            SELECT j.reference AS reference, le.direction AS direction,
                   le.amount AS amount, le.posted_at AS postedAt
            FROM ledger_entries le
            JOIN journal_entries j ON le.journal_id = j.id
            WHERE le.ledger_account_id = :ledgerAccountId
            ORDER BY le.posted_at
            """, nativeQuery = true)
    List<LedgerLineView> findLedgerLines(@Param("ledgerAccountId") UUID ledgerAccountId);
}
