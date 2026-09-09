package com.finledger.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.finledger.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end smoke test for Phase 0: boots the full application against a real
 * PostgreSQL container (Flyway migrations applied, Hibernate schema validated),
 * then hits the ping endpoint over HTTP and asserts the value round-tripped from
 * the database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PingControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void pingReturnsOkAndAppNameFromDatabase() {
        ResponseEntity<PingController.PingResponse> response =
                restTemplate.getForEntity("/api/v1/ping", PingController.PingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ok");
        assertThat(response.getBody().app()).isEqualTo("finledger");
        assertThat(response.getBody().time()).isNotNull();
    }
}
