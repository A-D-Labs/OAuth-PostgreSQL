package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/** Email/password authentication failed. Message is deliberately non-specific. */
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "auth.login.failure", detail);
    }
}
