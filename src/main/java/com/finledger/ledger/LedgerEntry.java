package com.finledger.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One debit or credit line belonging to a journal. Append-only: never updated or
 * deleted (enforced by a database trigger). The amount is always positive; the
 * direction says whether it is a debit or a credit.
 *
 * The journal and ledger-account links are stored as plain UUID foreign keys (not
 * JPA relationships) to keep the write path simple and explicit.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Set by the database default (NOW()); read-only from the application's side. */
    @Column(name = "posted_at", insertable = false, updatable = false)
    private Instant postedAt;

    protected LedgerEntry() {
        // required by JPA
    }

    public LedgerEntry(UUID journalId, UUID ledgerAccountId, EntryDirection direction,
                       BigDecimal amount, String currency) {
        this.journalId = journalId;
        this.ledgerAccountId = ledgerAccountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJournalId() {
        return journalId;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}
