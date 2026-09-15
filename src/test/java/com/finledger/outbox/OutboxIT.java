package com.finledger.outbox;

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
 * Verifies the transactional outbox: money movements write a PENDING event in the same
 * transaction, and a failed movement writes no event at all (atomicity).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OutboxIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void depositWritesPendingOutboxEvent() {
        String a = createAccount();
        deposit(a, "1000.00");

        // The event is recorded; it may already be PUBLISHED by the background publisher.
        assertThat(outbox.findByAggregateId(a))
                .anyMatch(e -> e.getEventType().equals("cash.deposited"));
    }

    @Test
    void successfulTransferAddsOneEventAndFailedTransferAddsNone() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "1000.00");

        long transfersBefore = outbox.countByEventType("transfer.completed");
        assertThat(transfer(a, b, "200.00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(outbox.countByEventType("transfer.completed")).isEqualTo(transfersBefore + 1);

        // A rejected transfer must leave the outbox untouched (same transaction rolled back).
        long allBefore = outbox.count();
        assertThat(transfer(a, b, "999999.00").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(outbox.count()).isEqualTo(allBefore);
    }

    @Test
    void outboxEventsEndpointReturnsEventsForAccount() {
        String a = createAccount();
        deposit(a, "500.00");

        ResponseEntity<List> response = rest.getForEntity(
                "/api/v1/outbox/events?aggregateId=" + a, List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().toString()).contains("cash.deposited");
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("sourceAccountId", from, "destinationAccountId", to,
                        "amount", new BigDecimal(amount), "currency", "CAD"),
                headers);
        return rest.exchange("/api/v1/transfers", HttpMethod.POST, entity, Map.class);
    }
}
