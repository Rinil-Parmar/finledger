-- V9: Authentication users table for JWT-based auth
-- Stores credentials (BCrypt hashed passwords) and roles for RBAC.

CREATE TABLE app_users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_users_email ON app_users (email);
