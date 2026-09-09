package com.finledger.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Trivial key/value row used only to prove JPA <-> Flyway <-> PostgreSQL are wired.
 * We map only the columns we read; {@code created_at} exists in the table but is left
 * unmapped, which Hibernate's {@code validate} mode permits.
 */
@Entity
@Table(name = "app_metadata")
public class AppMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meta_key", nullable = false, unique = true)
    private String metaKey;

    @Column(name = "meta_value", nullable = false)
    private String metaValue;

    protected AppMetadata() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public String getMetaKey() {
        return metaKey;
    }

    public String getMetaValue() {
        return metaValue;
    }
}
