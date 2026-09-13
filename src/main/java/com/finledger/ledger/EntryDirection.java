package com.finledger.ledger;

/**
 * The two sides of double-entry bookkeeping. Used both for a ledger entry's direction
 * and for a ledger account's "normal balance" (the side on which it increases).
 */
public enum EntryDirection {
    DEBIT,
    CREDIT
}
