package com.finledger.ledger;

import java.math.BigDecimal;

/**
 * A projection of one cash movement (a ledger entry on the CASH account), identified by
 * its journal reference. Used by reconciliation to compare internal cash activity against
 * the external institution's settlement records.
 */
public interface CashMovementView {

    String getReference();

    String getDirection();

    BigDecimal getAmount();

    String getCurrency();
}
