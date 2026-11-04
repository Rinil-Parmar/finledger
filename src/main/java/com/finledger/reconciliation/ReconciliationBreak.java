package com.finledger.reconciliation;

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

/** One discrepancy found during a reconciliation run. */
@Entity
@Table(name = "reconciliation_breaks")
public class ReconciliationBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_type", nullable = false)
    private BreakType breakType;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "internal_amount")
    private BigDecimal internalAmount;

    @Column(name = "external_amount")
    private BigDecimal externalAmount;

    @Column(name = "detail")
    private String detail;

    protected ReconciliationBreak() {
        // required by JPA
    }

    public ReconciliationBreak(UUID runId, BreakType breakType, String reference,
                               BigDecimal internalAmount, BigDecimal externalAmount, String detail) {
        this.runId = runId;
        this.breakType = breakType;
        this.reference = reference;
        this.internalAmount = internalAmount;
        this.externalAmount = externalAmount;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public BreakType getBreakType() {
        return breakType;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getInternalAmount() {
        return internalAmount;
    }

    public BigDecimal getExternalAmount() {
        return externalAmount;
    }

    public String getDetail() {
        return detail;
    }
}
