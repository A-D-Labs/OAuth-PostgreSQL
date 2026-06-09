package com.template.OAuth.validation;

import com.template.OAuth.exception.ApiException;
import com.template.OAuth.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageService messageService;

    @Autowired
    public GlobalExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = (error instanceof FieldError)
                    ? ((FieldError) error).getField()
                    : error.getObjectName(); // fall back to object name for non-field errors

            // Get localized error message if message code is available
            String defaultMessage = error.getDefaultMessage();
            String errorMessage;

            if (defaultMessage != null && defaultMessage.startsWith("{") && defaultMessage.endsWith("}")) {
                // Extract message code from {code}
                String messageKey = defaultMessage.substring(1, defaultMessage.length() - 1);
                errorMessage = messageService.getMessage(messageKey);
            } else if (defaultMessage != null) {
                errorMessage = defaultMessage;
            } else {
                // Sensible fallback if no default message present
                errorMessage = messageService.getMessage("validation.default");
            }

            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse response = new ValidationErrorResponse(
                messageService.getMessage("validation.error"),
                errors);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Expected, client-facing auth errors. The exception itself names the status and the i18n
     * message key, so we never leak internal exception text and always return the right code.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", messageService.getMessage(ex.getMessageKey()));
        errorResponse.put("error", ex.getClass().getSimpleName());
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    /**
     * Anything not modelled as an {@link ApiException} is unexpected. Log the detail server-side
     * for diagnosis but return only a generic message — never the raw exception text.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled runtime exception", ex);

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", messageService.getMessage("error.unknown"));
        errorResponse.put("error", "InternalError");

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
