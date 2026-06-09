package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/** A one-time token (email verification or password reset) was not recognized. */
public class InvalidTokenException extends ApiException {
    public InvalidTokenException(String detail) {
        super(HttpStatus.BAD_REQUEST, "auth.token.invalid", detail);
    }
}
