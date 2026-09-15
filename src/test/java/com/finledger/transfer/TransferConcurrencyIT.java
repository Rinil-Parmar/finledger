package com.finledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.finledger.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Proves balances are concurrency-safe: many parallel transfers from one account never
 * overdraw it. The account holds exactly enough for 10 transfers, so out of 20 fired at
 * once, exactly 10 succeed and 10 are rejected for insufficient funds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferConcurrencyIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void parallelTransfersNeverOverdrawTheAccount() throws Exception {
        String a = createAccount();
        String b = createAccount();
        deposit(a, "1000.00");

        int attempts = 20;                 // 20 concurrent transfers of 100 each...
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                startGun.await();          // ...released at the same instant
                return transfer(a, b, "100.00").getStatusCode().value();
            }));
        }
        startGun.countDown();

        int succeeded = 0;
        for (Future<Integer> r : results) {
            if (r.get() == 201) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(10);                       // only 10 could be funded
        assertThat(balanceOf(a)).isEqualByComparingTo("0");        // never negative
        assertThat(balanceOf(b)).isEqualByComparingTo("1000.00");  // all money accounted for
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

    private BigDecimal balanceOf(String id) {
        ResponseEntity<Map> r = rest.getForEntity("/api/v1/accounts/" + id + "/balance", Map.class);
        return new BigDecimal(r.getBody().get("balance").toString());
    }
}
