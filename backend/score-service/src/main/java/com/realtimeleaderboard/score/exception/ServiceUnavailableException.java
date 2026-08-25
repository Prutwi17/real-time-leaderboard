package com.realtimeleaderboard.score.exception;

/**
 * An upstream dependency (e.g. sport-service) is unavailable and the score
 * cannot be validated. The request is rejected rather than silently accepted.
 * Maps to HTTP 503 SERVICE UNAVAILABLE.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) { super(message); }
}
