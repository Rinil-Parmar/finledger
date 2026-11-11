package com.finledger.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One financial event (e.g. a single deposit). Its ledger entries must balance
 * (total debits == total credits). Journals are immutable once written.
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private JournalType type;

    @Column(name = "description")
    private String description;

    /** For a REVERSAL journal, the id of the journal it reverses; null otherwise. */
    @Column(name = "reverses_journal_id")
    private UUID reversesJournalId;

    @Column(name = "performed_by")
    private String performedBy;

    protected JournalEntry() {
        // required by JPA
    }

    public JournalEntry(String reference, JournalType type, String description) {
        this(reference, type, description, null, null);
    }

    public JournalEntry(String reference, JournalType type, String description, UUID reversesJournalId) {
        this(reference, type, description, reversesJournalId, null);
    }

    public JournalEntry(String reference, JournalType type, String description,
                         UUID reversesJournalId, String performedBy) {
        this.reference = reference;
        this.type = type;
        this.description = description;
        this.reversesJournalId = reversesJournalId;
        this.performedBy = performedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public JournalType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public UUID getReversesJournalId() {
        return reversesJournalId;
    }

    public String getPerformedBy() {
        return performedBy;
    }
}
