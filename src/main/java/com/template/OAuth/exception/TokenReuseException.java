package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/**
 * A refresh token that had already been rotated away was presented again. This is the
 * signature of a stolen token being replayed, so the whole token family is revoked and the
 * user must re-authenticate.
 */
public class TokenReuseException extends ApiException {
    public TokenReuseException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "auth.token.reuse", detail);
    }
}
