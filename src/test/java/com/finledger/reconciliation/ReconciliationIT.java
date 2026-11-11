package com.finledger.reconciliation;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies reconciliation: a matching external record produces no break, and each kind of
 * discrepancy (missing external, missing internal, amount mismatch) is detected and typed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
class ReconciliationIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void matchingRecordsProduceNoBreak() {
        String ref = deposit(createAccount(), "1000.00");   // internal CASH debit 1000
        recordExternal(ref, "DEBIT", "1000.00");            // external matches

        String runId = runReconciliation();
        assertThat(breakFor(runId, ref)).isNull();
    }

    @Test
    void movementWithNoExternalRecordIsMissingExternal() {
        String ref = deposit(createAccount(), "1000.00");   // no external recorded

        String runId = runReconciliation();
        assertThat(breakTypeFor(runId, ref)).isEqualTo("MISSING_EXTERNAL");
    }

    @Test
    void amountDifferenceIsAmountMismatch() {
        String ref = deposit(createAccount(), "1000.00");
        recordExternal(ref, "DEBIT", "500.00");             // external disagrees

        String runId = runReconciliation();
        assertThat(breakTypeFor(runId, ref)).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    void externalRecordWithNoLedgerMovementIsMissingInternal() {
        String ghostRef = "ext_" + UUID.randomUUID().toString().substring(0, 8);
        recordExternal(ghostRef, "DEBIT", "100.00");        // external only, no internal

        String runId = runReconciliation();
        assertThat(breakTypeFor(runId, ghostRef)).isEqualTo("MISSING_INTERNAL");
    }

    private String createAccount() {
        ResponseEntity<Map> r = rest.postForEntity(
                "/api/v1/accounts", Map.of("ownerName", "Test", "currency", "CAD"), Map.class);
        return (String) r.getBody().get("externalId");
    }

    private String deposit(String id, String amount) {
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/accounts/" + id + "/deposits",
                Map.of("amount", new BigDecimal(amount), "currency", "CAD"), Map.class);
        return (String) r.getBody().get("journalReference");
    }

    private void recordExternal(String reference, String direction, String amount) {
        rest.postForEntity("/api/v1/reconciliation/external",
                Map.of("reference", reference, "direction", direction,
                        "amount", new BigDecimal(amount), "currency", "CAD"),
                Map.class);
    }

    private String runReconciliation() {
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/reconciliation/runs", null, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) r.getBody().get("runId");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> breakFor(String runId, String reference) {
        ResponseEntity<List> r = rest.getForEntity(
                "/api/v1/reconciliation/runs/" + runId + "/breaks", List.class);
        List<Map<String, Object>> list = r.getBody();
        return list.stream()
                .filter(b -> reference.equals(b.get("reference")))
                .findFirst()
                .orElse(null);
    }

    private String breakTypeFor(String runId, String reference) {
        Map<String, Object> b = breakFor(runId, reference);
        assertThat(b).as("expected a break for reference " + reference).isNotNull();
        return (String) b.get("breakType");
    }
}

