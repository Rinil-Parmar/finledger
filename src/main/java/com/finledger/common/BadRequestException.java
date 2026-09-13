package com.finledger.common;

/** Thrown when a request is invalid (e.g. a non-positive amount). Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
