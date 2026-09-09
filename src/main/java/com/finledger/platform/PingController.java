package com.finledger.platform;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke-test endpoint. Reads a value back from PostgreSQL through JPA so a single
 * HTTP call exercises the whole stack: HTTP -> service -> repository -> database.
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final AppMetadataRepository metadataRepository;

    public PingController(AppMetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        String app = metadataRepository.findByMetaKey("app.name")
                .map(AppMetadata::getMetaValue)
                .orElse("unknown");
        return new PingResponse("ok", app, Instant.now());
    }

    public record PingResponse(String status, String app, Instant time) {
    }
}
