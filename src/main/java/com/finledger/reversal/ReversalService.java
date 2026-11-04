package com.finledger.reversal;

import com.finledger.common.BadRequestException;
import com.finledger.common.ConflictException;
import com.finledger.common.NotFoundException;
import com.finledger.ledger.EntryDirection;
import com.finledger.ledger.JournalEntry;
import com.finledger.ledger.JournalEntryRepository;
import com.finledger.ledger.JournalType;
import com.finledger.ledger.LedgerEntry;
import com.finledger.ledger.LedgerEntryRepository;
import com.finledger.ledger.LedgerService;
import com.finledger.ledger.PostingLine;
import com.finledger.outbox.OutboxService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reverses a previously posted journal by posting a compensating journal whose entries
 * mirror the original (debits and credits swapped). The original is never modified; the
 * reversal is a new, linked event, so the full history stays intact and auditable.
 */
@Service
public class ReversalService {

    private final JournalEntryRepository journals;
    private final LedgerEntryRepository entries;
    private final LedgerService ledgerService;
    private final OutboxService outbox;

    public ReversalService(JournalEntryRepository journals, LedgerEntryRepository entries,
                           LedgerService ledgerService, OutboxService outbox) {
        this.journals = journals;
        this.entries = entries;
        this.ledgerService = ledgerService;
        this.outbox = outbox;
    }

    @Transactional
    public ReversalResult reverse(String reference) {
        JournalEntry original = journals.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Journal not found: " + reference));

        if (original.getType() == JournalType.REVERSAL) {
            throw new BadRequestException("A reversal cannot itself be reversed: " + reference);
        }
        if (journals.existsByReversesJournalId(original.getId())) {
            throw new ConflictException("Journal has already been reversed: " + reference);
        }

        // Mirror every line of the original: a debit becomes a credit and vice versa.
        List<PostingLine> mirrored = entries.findByJournalId(original.getId()).stream()
                .map(e -> new PostingLine(
                        e.getLedgerAccountId(),
                        e.getDirection() == EntryDirection.DEBIT ? EntryDirection.CREDIT : EntryDirection.DEBIT,
                        e.getAmount(),
                        e.getCurrency()))
                .toList();

        JournalEntry reversal = ledgerService.post(
                JournalType.REVERSAL, "Reversal of " + reference, mirrored, original.getId());

        outbox.append("journal", reversal.getReference(), "journal.reversed", Map.of(
                "reversalReference", reversal.getReference(),
                "originalReference", reference));

        return new ReversalResult(reference, reversal.getReference());
    }

    public record ReversalResult(String originalReference, String reversalReference) {
    }
}
