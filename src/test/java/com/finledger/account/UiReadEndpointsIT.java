package com.finledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.finledger.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the read endpoints the UI depends on: list accounts with balances, a wallet's
 * ledger lines, and the assets = liabilities totals.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UiReadEndpointsIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void listAccountsIncludesDerivedBalances() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "1000.00");
        deposit(b, "500.00");
        transfer(a, b, "200.00");   // a: 800, b: 700

        ResponseEntity<List> response = rest.getForEntity("/api/v1/accounts", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> accounts = response.getBody();

        assertThat(balanceInList(accounts, a)).isEqualByComparingTo("800.00");
        assertThat(balanceInList(accounts, b)).isEqualByComparingTo("700.00");
    }

    @Test
    void entriesEndpointReturnsLedgerLines() {
        String a = createAccount();
        deposit(a, "1000.00");

        ResponseEntity<List> response = rest.getForEntity("/api/v1/accounts/" + a + "/entries", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void totalsSatisfyTheAssetsEqualsLiabilitiesInvariant() {
        deposit(createAccount(), "250.00");   // ensure there is some activity

        ResponseEntity<Map> response = rest.getForEntity("/api/v1/ledger/totals", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        BigDecimal cashPool = new BigDecimal(response.getBody().get("cashPool").toString());
        BigDecimal totalLiabilities = new BigDecimal(response.getBody().get("totalLiabilities").toString());
        assertThat(cashPool).isEqualByComparingTo(totalLiabilities);
        assertThat(response.getBody().get("balanced")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal balanceInList(List<Map<String, Object>> accounts, String externalId) {
        Map<String, Object> match = accounts.stream()
                .filter(acc -> externalId.equals(acc.get("externalId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("account not in list: " + externalId));
        return new BigDecimal(match.get("balance").toString());
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

    private void transfer(String from, String to, String amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("sourceAccountId", from, "destinationAccountId", to,
                        "amount", new BigDecimal(amount), "currency", "CAD"),
                headers);
        rest.exchange("/api/v1/transfers", HttpMethod.POST, entity, Map.class);
    }
}
