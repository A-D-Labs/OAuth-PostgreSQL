package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/** The presented refresh token was valid but has passed its expiry. */
public class RefreshTokenExpiredException extends ApiException {
    public RefreshTokenExpiredException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "auth.token.expired", detail);
    }
}
