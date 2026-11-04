package com.finledger.ledger;

import java.math.BigDecimal;
import java.time.Instant;

/** One ledger line for an account, with its journal reference (used by the UI explorer). */
public interface LedgerLineView {

    String getReference();

    String getDirection();

    BigDecimal getAmount();

    Instant getPostedAt();
}
