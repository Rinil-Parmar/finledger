package com.finledger.cash;

import static org.assertj.core.api.Assertions.assertThat;

import com.finledger.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises the full deposit flow over HTTP against a real PostgreSQL:
 * create account -> deposit -> deposit -> check balance, and verifies the
 * global double-entry invariant (total debits == total credits) in the database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DepositFlowIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void depositsAccumulateAndBalanceIsDerivedFromTheLedger() {
        // Create an account.
        ResponseEntity<Map> created = rest.postForEntity(
                "/api/v1/accounts", Map.of("ownerName", "Rinil", "currency", "CAD"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String externalId = (String) created.getBody().get("externalId");
        assertThat(externalId).startsWith("acc_");

        // Deposit 1000, then 500.
        BigDecimal afterFirst = deposit(externalId, "1000.00");
        assertThat(afterFirst).isEqualByComparingTo("1000.00");

        BigDecimal afterSecond = deposit(externalId, "500.00");
        assertThat(afterSecond).isEqualByComparingTo("1500.00");

        // Balance endpoint agrees.
        ResponseEntity<Map> balance = rest.getForEntity(
                "/api/v1/accounts/" + externalId + "/balance", Map.class);
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(balance.getBody().get("balance").toString()))
                .isEqualByComparingTo("1500.00");

        // Global invariant: across the whole ledger, debits must equal credits.
        BigDecimal debits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE direction = 'DEBIT'", BigDecimal.class);
        BigDecimal credits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE direction = 'CREDIT'", BigDecimal.class);
        assertThat(debits).isEqualByComparingTo(credits);
    }

    @Test
    void negativeDepositIsRejected() {
        ResponseEntity<Map> created = rest.postForEntity(
                "/api/v1/accounts", Map.of("ownerName", "Test", "currency", "CAD"), Map.class);
        String externalId = (String) created.getBody().get("externalId");

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/accounts/" + externalId + "/deposits",
                Map.of("amount", new BigDecimal("-5.00"), "currency", "CAD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private BigDecimal deposit(String externalId, String amount) {
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/accounts/" + externalId + "/deposits",
                Map.of("amount", new BigDecimal(amount), "currency", "CAD"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new BigDecimal(response.getBody().get("newBalance").toString());
    }
}
