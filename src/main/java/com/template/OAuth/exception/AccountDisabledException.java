package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/** The account exists but is not yet enabled (email not verified). */
public class AccountDisabledException extends ApiException {
    public AccountDisabledException(String detail) {
        super(HttpStatus.FORBIDDEN, "auth.account.disabled", detail);
    }
}
