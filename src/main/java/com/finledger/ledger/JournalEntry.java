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

    protected JournalEntry() {
        // required by JPA
    }

    public JournalEntry(String reference, JournalType type, String description) {
        this.reference = reference;
        this.type = type;
        this.description = description;
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
}
