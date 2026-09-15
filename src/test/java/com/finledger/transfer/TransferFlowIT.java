package com.finledger.transfer;

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
 * Verifies account-to-account transfers over HTTP: money moves from source to
 * destination, and invalid transfers (insufficient funds, same account, unknown
 * account) are rejected without moving anything.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferFlowIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void transferMovesMoneyBetweenAccounts() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "1000.00");

        ResponseEntity<Map> response = transfer(a, b, "300.00");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(new BigDecimal(response.getBody().get("sourceBalance").toString())).isEqualByComparingTo("700.00");
        assertThat(new BigDecimal(response.getBody().get("destinationBalance").toString())).isEqualByComparingTo("300.00");

        assertThat(balanceOf(a)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("300.00");
    }

    @Test
    void transferBeyondBalanceIsRejected() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "100.00");

        assertThat(transfer(a, b, "500.00").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(balanceOf(a)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("0");
    }

    @Test
    void transferToSameAccountIsRejected() {
        String a = createAccount();
        deposit(a, "100.00");
        assertThat(transfer(a, a, "10.00").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transferToUnknownAccountIsNotFound() {
        String a = createAccount();
        deposit(a, "100.00");
        assertThat(transfer(a, "acc_does_not_exist", "10.00").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String createAccount() {
        ResponseEntity<Map> r = rest.postForEntity(
                "/api/v1/accounts", Map.of("ownerName", "Test", "currency", "CAD"), Map.class);
        return (String) r.getBody().get("externalId");
    }

    private void deposit(String id, String amount) {
        rest.postForEntity("/api/v1/accounts/" + id + "/deposits",
                Map.of("amount", new BigDecimal(amount), "currency", "CAD"), Map.class);
    }

    private ResponseEntity<Map> transfer(String from, String to, String amount) {
        return rest.postForEntity("/api/v1/transfers",
                Map.of("sourceAccountId", from, "destinationAccountId", to,
                        "amount", new BigDecimal(amount), "currency", "CAD"), Map.class);
    }

    private BigDecimal balanceOf(String id) {
        ResponseEntity<Map> r = rest.getForEntity("/api/v1/accounts/" + id + "/balance", Map.class);
        return new BigDecimal(r.getBody().get("balance").toString());
    }
}
