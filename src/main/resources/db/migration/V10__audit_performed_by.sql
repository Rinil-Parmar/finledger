-- V10: Track which authenticated user performed each journal entry.
-- Nullable because pre-auth journals have no actor.

ALTER TABLE journal_entries
    ADD COLUMN performed_by VARCHAR(255);
