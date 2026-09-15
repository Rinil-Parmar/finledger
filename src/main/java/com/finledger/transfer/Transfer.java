package com.finledger.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A first-class record of a money transfer between two accounts. The actual money
 * movement lives in the ledger; this row links to the journal that performed it.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "dest_account_id", nullable = false)
    private UUID destAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransferStatus status;

    @Column(name = "journal_id")
    private UUID journalId;

    protected Transfer() {
        // required by JPA
    }

    public Transfer(String reference, UUID sourceAccountId, UUID destAccountId,
                    BigDecimal amount, String currency, TransferStatus status, UUID journalId) {
        this.reference = reference;
        this.sourceAccountId = sourceAccountId;
        this.destAccountId = destAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.journalId = journalId;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestAccountId() {
        return destAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public UUID getJournalId() {
        return journalId;
    }
}
