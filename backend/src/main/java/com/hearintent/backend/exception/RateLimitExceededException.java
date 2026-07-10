package com.hearintent.backend.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApplicationException {
    public RateLimitExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", message);
    }
}
