package com.finledger.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One execution of reconciliation, with its summary counts. */
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatus status;

    @Column(name = "total_matched", nullable = false)
    private int totalMatched;

    @Column(name = "total_breaks", nullable = false)
    private int totalBreaks;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ReconciliationRun() {
        this.status = RunStatus.RUNNING;
    }

    public void complete(int totalMatched, int totalBreaks) {
        this.totalMatched = totalMatched;
        this.totalBreaks = totalBreaks;
        this.status = RunStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public RunStatus getStatus() {
        return status;
    }

    public int getTotalMatched() {
        return totalMatched;
    }

    public int getTotalBreaks() {
        return totalBreaks;
    }
}
