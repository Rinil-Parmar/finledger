package com.finledger.reconciliation;

/** The kinds of discrepancy reconciliation can find between internal and external records. */
public enum BreakType {
    MISSING_EXTERNAL,   // in our ledger, but the external institution has no record
    MISSING_INTERNAL,   // the external institution has a record, but we don't
    AMOUNT_MISMATCH,    // both exist, amounts differ
    DIRECTION_MISMATCH  // both exist, but one is a debit and the other a credit
}
