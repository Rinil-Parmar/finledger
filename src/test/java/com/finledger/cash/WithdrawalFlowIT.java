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

/**
 * Verifies withdrawals over HTTP: a withdrawal reduces the balance, and a withdrawal
 * larger than the balance is rejected without changing anything.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WithdrawalFlowIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void withdrawalReducesBalance() {
        String id = createAccount();
        deposit(id, "1000.00");

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/accounts/" + id + "/withdrawals",
                Map.of("amount", new BigDecimal("300.00"), "currency", "CAD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(new BigDecimal(response.getBody().get("newBalance").toString()))
                .isEqualByComparingTo("700.00");
    }

    @Test
    void withdrawalBeyondBalanceIsRejectedAndChangesNothing() {
        String id = createAccount();
        deposit(id, "1000.00");

        ResponseEntity<Map> response = rest.postForEntity(
                "/api/v1/accounts/" + id + "/withdrawals",
                Map.of("amount", new BigDecimal("5000.00"), "currency", "CAD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Balance is untouched: still 1000.
        ResponseEntity<Map> balance = rest.getForEntity(
                "/api/v1/accounts/" + id + "/balance", Map.class);
        assertThat(new BigDecimal(balance.getBody().get("balance").toString()))
                .isEqualByComparingTo("1000.00");
    }

    private String createAccount() {
        ResponseEntity<Map> created = rest.postForEntity(
                "/api/v1/accounts", Map.of("ownerName", "Test", "currency", "CAD"), Map.class);
        return (String) created.getBody().get("externalId");
    }

    private void deposit(String id, String amount) {
        rest.postForEntity("/api/v1/accounts/" + id + "/deposits",
                Map.of("amount", new BigDecimal(amount), "currency", "CAD"), Map.class);
    }
}
