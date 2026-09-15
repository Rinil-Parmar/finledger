package com.finledger.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.finledger.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
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
 * End-to-end proof of Phase 3.2: a transfer writes an outbox event, the scheduled
 * publisher delivers it to a real Kafka broker, the event flips to PUBLISHED, and the
 * consumer receives it. Runs against real PostgreSQL and Kafka containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OutboxPublishingIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private EventConsumer consumer;

    @Test
    void transferEventIsPublishedToKafkaAndConsumed() {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "1000.00");

        ResponseEntity<Map> response = transfer(a, b, "250.00");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reference = (String) response.getBody().get("reference");

        // The publisher runs on a timer and Kafka delivery is async, so wait for it.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<OutboxEvent> events = outbox.findByAggregateId(reference);
            assertThat(events).isNotEmpty();
            assertThat(events).allMatch(e -> e.getStatus() == OutboxStatus.PUBLISHED);
            assertThat(consumer.getReceived()).anyMatch(s -> s.contains(reference));
        });
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
