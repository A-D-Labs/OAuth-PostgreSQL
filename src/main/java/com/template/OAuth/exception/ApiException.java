package com.template.OAuth.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected, client-facing auth errors. Each carries the HTTP status to return
 * and an i18n message key, so the exception handler can respond with the right code and a
 * localized message without ever echoing internal exception text to the client.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;

    protected ApiException(HttpStatus status, String messageKey, String detail) {
        super(detail);
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
