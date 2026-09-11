-- V3: Enforce the append-only ledger at the database level.
--
-- Financial history must never be edited or deleted. Corrections are made by
-- posting new, reversing entries -- not by mutating past rows. Enforcing this in
-- the database (not just the application) means even a buggy app or a manual SQL
-- statement cannot rewrite history.

CREATE OR REPLACE FUNCTION reject_ledger_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Ledger history is append-only: % on table % is not allowed. Post a reversing entry instead.',
        TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_entries_immutable
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
