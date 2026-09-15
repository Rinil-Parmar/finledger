package com.finledger.common;

/** Thrown on an idempotency conflict (key reused with a different request). Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
