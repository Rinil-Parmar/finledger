package com.finledger.reconciliation;

import com.finledger.ledger.EntryDirection;
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
 * A settlement record from the external institution (simulated). Reconciliation matches
 * these against the internal CASH movements by reference.
 */
@Entity
@Table(name = "external_transactions")
public class ExternalTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected ExternalTransaction() {
        // required by JPA
    }

    public ExternalTransaction(String reference, EntryDirection direction, BigDecimal amount, String currency) {
        this.reference = reference;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
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
}
