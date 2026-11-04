-- V7: Reversals (compensating entries).
--
-- A reversal is a NEW journal whose entries mirror an original journal (debits and
-- credits swapped), bringing the net effect to zero. History is never edited; we only
-- add. The reversal journal points back at the journal it reverses, which also lets us
-- detect (without mutating the original) whether a journal has already been reversed.

ALTER TABLE journal_entries
    ADD COLUMN reverses_journal_id UUID REFERENCES journal_entries (id);

CREATE INDEX idx_journal_reverses ON journal_entries (reverses_journal_id);
