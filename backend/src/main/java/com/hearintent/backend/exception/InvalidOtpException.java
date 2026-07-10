package com.hearintent.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidOtpException extends ApplicationException {
    public InvalidOtpException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_OTP", message);
    }
}
