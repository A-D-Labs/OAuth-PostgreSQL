package com.template.OAuth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

@Service
public class MessageService {

    private final MessageSource messageSource;

    @Autowired
    public MessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Get a message in the user's current locale
     *
     * @param code Message code from properties file
     * @return Localized message
     */
    @NonNull
    @SuppressWarnings("null")
    public String getMessage(@NonNull String code) {
        Locale locale = LocaleContextHolder.getLocale();
        return Objects.requireNonNull(messageSource.getMessage(code, null, code, locale));
    }

    /**
     * Get a message with parameters in the user's current locale
     *
     * @param code Message code from properties file
     * @param args Parameters to substitute in the message
     * @return Localized message with parameters
     */
    @NonNull
    @SuppressWarnings("null")
    public String getMessageWithArgs(@NonNull String code, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        return Objects.requireNonNull(messageSource.getMessage(code, args, code, locale));
    }

    /**
     * Get a message in a specific locale
     *
     * @param code   Message code from properties file
     * @param locale Specific locale to use
     * @return Localized message
     */
    @NonNull
    @SuppressWarnings("null")
    public String getMessageForLocale(@NonNull String code, @NonNull Locale locale) {
        return Objects.requireNonNull(messageSource.getMessage(code, null, code, locale));
    }
}