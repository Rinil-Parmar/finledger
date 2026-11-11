package com.finledger.ledger;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ledger-wide read endpoints for the UI. {@code /totals} exposes the core accounting
 * invariant: the pooled CASH asset should always equal the sum of customer liabilities.
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerAccountRepository ledgerAccounts;
    private final LedgerService ledgerService;
    private final JournalEntryRepository journals;

    public LedgerController(LedgerAccountRepository ledgerAccounts, LedgerService ledgerService,
                            JournalEntryRepository journals) {
        this.ledgerAccounts = ledgerAccounts;
        this.ledgerService = ledgerService;
        this.journals = journals;
    }

    @GetMapping("/totals")
    public TotalsResponse totals() {
        BigDecimal cashPool = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;

        for (LedgerAccount account : ledgerAccounts.findAll()) {
            // creditPositiveBalance = credits - debits. Assets grow on the debit side, so
            // their balance is the negation; liabilities grow on the credit side as-is.
            BigDecimal creditPositive = ledgerService.creditPositiveBalance(account.getId());
            if (account.getAccountType() == LedgerAccountType.ASSET) {
                cashPool = cashPool.add(creditPositive.negate());
            } else if (account.getAccountType() == LedgerAccountType.LIABILITY) {
                totalLiabilities = totalLiabilities.add(creditPositive);
            }
        }

        return new TotalsResponse(cashPool, totalLiabilities, cashPool.compareTo(totalLiabilities) == 0);
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit() {
        return journals.findTop50ByOrderByIdDesc().stream()
                .map(j -> new AuditEntry(
                        j.getReference(),
                        j.getType().name(),
                        j.getDescription(),
                        j.getPerformedBy()))
                .toList();
    }

    public record TotalsResponse(BigDecimal cashPool, BigDecimal totalLiabilities, boolean balanced) {
    }

    public record AuditEntry(String reference, String type, String description, String performedBy) {
    }
}
