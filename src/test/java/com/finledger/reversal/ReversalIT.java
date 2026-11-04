package com.finledger.reversal;

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
 * Verifies compensating reversals: reversing a deposit returns the balance to zero via a
 * mirror journal, a journal cannot be reversed twice, and an unknown reference is a 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ReversalIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void reversingADepositReturnsTheBalanceToZero() {
        String a = createAccount();
        String journalRef = deposit(a, "1000.00");
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.00");

        ResponseEntity<Map> reversal = reverse(journalRef);
        assertThat(reversal.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reversal.getBody().get("reversalReference")).isNotNull();

        assertThat(balanceOf(a)).isEqualByComparingTo("0");
    }

    @Test
    void aJournalCannotBeReversedTwice() {
        String a = createAccount();
        String journalRef = deposit(a, "500.00");

        assertThat(reverse(journalRef).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reverse(journalRef).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(balanceOf(a)).isEqualByComparingTo("0");
    }

    @Test
    void reversingAnUnknownJournalIsNotFound() {
        assertThat(reverse("jnl_does_not_exist").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

    private ResponseEntity<Map> reverse(String reference) {
        return rest.postForEntity("/api/v1/reversals", Map.of("reference", reference), Map.class);
    }

    private BigDecimal balanceOf(String id) {
        ResponseEntity<Map> r = rest.getForEntity("/api/v1/accounts/" + id + "/balance", Map.class);
        return new BigDecimal(r.getBody().get("balance").toString());
    }
}
