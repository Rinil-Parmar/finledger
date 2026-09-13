package com.finledger.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One intended line of a journal before it is posted: which ledger account, which side
 * (debit or credit), how much, and in what currency.
 */
public record PostingLine(UUID ledgerAccountId, EntryDirection direction, BigDecimal amount, String currency) {
}
