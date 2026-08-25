package com.realtimeleaderboard.sport.exception;

/**
 * The request is syntactically valid but violates a domain rule, e.g.
 * deleting a sport that still owns competitions. Maps to HTTP 409 CONFLICT.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
