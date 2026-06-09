package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/** A one-time token (email verification or password reset) was valid but has expired. */
public class TokenExpiredException extends ApiException {
    public TokenExpiredException(String detail) {
        super(HttpStatus.BAD_REQUEST, "auth.token.expired", detail);
    }
}
