package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/** The presented refresh token does not match any active or recently-rotated token. */
public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "auth.token.invalid", detail);
    }
}
