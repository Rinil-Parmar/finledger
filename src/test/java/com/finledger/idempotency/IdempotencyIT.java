package com.finledger.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.finledger.TestcontainersConfiguration;
import java.math.BigDecimal;
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
 * Verifies the idempotency guarantee on transfers: the same Idempotency-Key moves money
 * exactly once, reusing a key with a different body is rejected, and the key is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IdempotencyIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void sameKeyMovesMoneyExactlyOnce() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "1000.00");
        String key = UUID.randomUUID().toString();

        ResponseEntity<Map> first = transfer(key, a, b, "200.00");
        ResponseEntity<Map> second = transfer(key, a, b, "200.00");   // identical retry

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // The retry returns the SAME stored response as the first call.
        assertThat(second.getBody().get("reference")).isEqualTo(first.getBody().get("reference"));

        // Money moved once, not twice.
        assertThat(balanceOf(a)).isEqualByComparingTo("800.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("200.00");
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "1000.00");
        String key = UUID.randomUUID().toString();

        transfer(key, a, b, "200.00");
        ResponseEntity<Map> conflicting = transfer(key, a, b, "999.00");   // same key, different amount

        assertThat(conflicting.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Only the first transfer took effect.
        assertThat(balanceOf(a)).isEqualByComparingTo("800.00");
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "100.00");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("sourceAccountId", a, "destinationAccountId", b,
                        "amount", new BigDecimal("10.00"), "currency", "CAD"),
                headers);
        ResponseEntity<Map> response = rest.exchange("/api/v1/transfers", HttpMethod.POST, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

    private ResponseEntity<Map> transfer(String key, String from, String to, String amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("sourceAccountId", from, "destinationAccountId", to,
                        "amount", new BigDecimal(amount), "currency", "CAD"),
                headers);
        return rest.exchange("/api/v1/transfers", HttpMethod.POST, entity, Map.class);
    }

    private BigDecimal balanceOf(String id) {
        ResponseEntity<Map> r = rest.getForEntity("/api/v1/accounts/" + id + "/balance", Map.class);
        return new BigDecimal(r.getBody().get("balance").toString());
    }
}
