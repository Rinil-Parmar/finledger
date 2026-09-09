-- V1: Baseline foundation migration.
--
-- Phase 0 intentionally models NO business domain yet. This migration exists only
-- to prove the full stack is wired end-to-end (Flyway -> PostgreSQL, validated by
-- Hibernate, reachable over HTTP). The real domain schema arrives in Phase 1.
--
-- Convention for the whole project: Flyway is the single source of truth for the
-- schema. Hibernate runs in `validate` mode and never mutates the database.

CREATE TABLE app_metadata (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    meta_key    VARCHAR(100) UNIQUE NOT NULL,
    meta_value  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO app_metadata (meta_key, meta_value) VALUES
    ('app.name', 'finledger'),
    ('schema.baseline', 'v1');
