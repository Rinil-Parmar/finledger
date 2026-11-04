package com.finledger.reconciliation;

import com.finledger.ledger.CashMovementView;
import com.finledger.ledger.EntryDirection;
import com.finledger.ledger.LedgerEntryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares the internal ledger's cash movements against the external institution's
 * settlement records and records any discrepancies as typed breaks.
 */
@Service
public class ReconciliationService {

    private final LedgerEntryRepository ledgerEntries;
    private final ExternalTransactionRepository externalTransactions;
    private final ReconciliationRunRepository runs;
    private final ReconciliationBreakRepository breaks;

    public ReconciliationService(LedgerEntryRepository ledgerEntries,
                                 ExternalTransactionRepository externalTransactions,
                                 ReconciliationRunRepository runs,
                                 ReconciliationBreakRepository breaks) {
        this.ledgerEntries = ledgerEntries;
        this.externalTransactions = externalTransactions;
        this.runs = runs;
        this.breaks = breaks;
    }

    /** Records a settlement record from the external institution (simulated). */
    @Transactional
    public ExternalTransaction recordExternal(String reference, EntryDirection direction,
                                              BigDecimal amount, String currency) {
        return externalTransactions.save(new ExternalTransaction(reference, direction, amount, currency));
    }

    /** Runs reconciliation: matches internal vs external by reference and records breaks. */
    @Transactional
    public ReconciliationRun run() {
        ReconciliationRun run = runs.save(new ReconciliationRun());

        Map<String, Movement> internal = new LinkedHashMap<>();
        for (CashMovementView m : ledgerEntries.findCashMovements()) {
            internal.put(m.getReference(), new Movement(m.getDirection(), m.getAmount()));
        }
        Map<String, Movement> external = new LinkedHashMap<>();
        for (ExternalTransaction e : externalTransactions.findAll()) {
            external.put(e.getReference(), new Movement(e.getDirection().name(), e.getAmount()));
        }

        int matched = 0;
        List<ReconciliationBreak> found = new ArrayList<>();

        for (Map.Entry<String, Movement> entry : internal.entrySet()) {
            String reference = entry.getKey();
            Movement in = entry.getValue();
            Movement ex = external.get(reference);
            if (ex == null) {
                found.add(new ReconciliationBreak(run.getId(), BreakType.MISSING_EXTERNAL, reference,
                        in.amount(), null, "In ledger but no external settlement record"));
            } else if (in.amount().compareTo(ex.amount()) != 0) {
                found.add(new ReconciliationBreak(run.getId(), BreakType.AMOUNT_MISMATCH, reference,
                        in.amount(), ex.amount(), "Amounts differ"));
            } else if (!in.direction().equals(ex.direction())) {
                found.add(new ReconciliationBreak(run.getId(), BreakType.DIRECTION_MISMATCH, reference,
                        in.amount(), ex.amount(),
                        "Direction differs: internal " + in.direction() + " vs external " + ex.direction()));
            } else {
                matched++;
            }
        }

        for (Map.Entry<String, Movement> entry : external.entrySet()) {
            if (!internal.containsKey(entry.getKey())) {
                found.add(new ReconciliationBreak(run.getId(), BreakType.MISSING_INTERNAL, entry.getKey(),
                        null, entry.getValue().amount(), "External settlement with no ledger record"));
            }
        }

        breaks.saveAll(found);
        run.complete(matched, found.size());
        return runs.save(run);
    }

    private record Movement(String direction, BigDecimal amount) {
    }
}
