package com.finledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finledger.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the append-only guarantee from V3: once a ledger row exists, the database
 * itself refuses to UPDATE or DELETE it. We insert directly via JDBC (no application
 * code involved) so the test verifies the database, not our services.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerImmutabilityIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ledgerEntriesCannotBeUpdatedOrDeleted() {
        // Use the seeded CASH ledger account and a fresh journal to hang an entry on.
        UUID cashAccountId = jdbc.queryForObject(
                "SELECT id FROM ledger_accounts WHERE code = 'CASH'", UUID.class);

        UUID journalId = UUID.randomUUID();
        jdbc.update("INSERT INTO journal_entries (id, reference, entry_type) VALUES (?, ?, ?)",
                journalId, "jnl_immutability_test", "DEPOSIT");

        UUID entryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ledger_entries
                    (id, journal_id, ledger_account_id, direction, amount, currency)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                entryId, journalId, cashAccountId, "DEBIT", new BigDecimal("100.0000"), "CAD");

        // Updating a ledger entry is rejected by the trigger.
        assertThatThrownBy(() ->
                jdbc.update("UPDATE ledger_entries SET amount = ? WHERE id = ?",
                        new BigDecimal("999.0000"), entryId))
                .hasMessageContaining("append-only");

        // Deleting a ledger entry is rejected by the trigger.
        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM ledger_entries WHERE id = ?", entryId))
                .hasMessageContaining("append-only");

        // The original row is still intact and unchanged.
        BigDecimal amount = jdbc.queryForObject(
                "SELECT amount FROM ledger_entries WHERE id = ?", BigDecimal.class, entryId);
        assertThat(amount).isEqualByComparingTo("100.0000");
    }
}
