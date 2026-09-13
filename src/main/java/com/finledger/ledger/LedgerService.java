package com.finledger.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that writes to the ledger. It guarantees the core invariant:
 * a journal is only persisted if its debits equal its credits.
 */
@Service
public class LedgerService {

    private final JournalEntryRepository journals;
    private final LedgerEntryRepository entries;

    public LedgerService(JournalEntryRepository journals, LedgerEntryRepository entries) {
        this.journals = journals;
        this.entries = entries;
    }

    /**
     * Posts a balanced journal. Creates the journal and all its ledger entries inside a
     * single transaction, but only after verifying that total debits == total credits.
     * If the lines do not balance, an exception is thrown and nothing is written.
     */
    @Transactional
    public JournalEntry post(JournalType type, String description, List<PostingLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("A journal must have at least two entries");
        }

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (PostingLine line : lines) {
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new IllegalArgumentException("Entry amounts must be positive");
            }
            if (line.direction() == EntryDirection.DEBIT) {
                debits = debits.add(line.amount());
            } else {
                credits = credits.add(line.amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            // A balanced journal is the fundamental rule of double-entry bookkeeping.
            throw new IllegalStateException(
                    "Unbalanced journal: debits " + debits + " != credits " + credits);
        }

        JournalEntry journal = journals.save(new JournalEntry(newReference(), type, description));
        for (PostingLine line : lines) {
            entries.save(new LedgerEntry(
                    journal.getId(), line.ledgerAccountId(), line.direction(), line.amount(), line.currency()));
        }
        return journal;
    }

    /** Balance of a ledger account computed as credits minus debits (correct for liabilities). */
    @Transactional(readOnly = true)
    public BigDecimal creditPositiveBalance(UUID ledgerAccountId) {
        return entries.deriveCreditPositiveBalance(ledgerAccountId);
    }

    private static String newReference() {
        return "jnl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
