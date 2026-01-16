package com.template.OAuth.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.Locale;

@Configuration
public class InternationalizationConfig implements WebMvcConfigurer {

    /**
     * Configure the message source to find message properties files
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false); // Prevent fallback to system locale
        return messageSource;
    }

    /**
     * Define a locale resolver that uses cookies to determine the user's locale
     */
    @Bean
    public LocaleResolver localeResolver() {
        // Spring 6+ recommends using the constructor instead of setCookieName()
        CookieLocaleResolver resolver = new CookieLocaleResolver("locale");
        resolver.setDefaultLocale(Locale.US); // Default to US English
        resolver.setCookieMaxAge(java.util.Objects.requireNonNull(Duration.ofHours(1))); // Cookie expires after 1 hour
        resolver.setCookiePath("/");
        return resolver;
    }

    /**
     * Define a locale change interceptor to switch languages via URL parameter
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang"); // Parameter name to switch language
        return interceptor;
    }

    /**
     * Register the locale change interceptor
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(java.util.Objects.requireNonNull(localeChangeInterceptor()));
    }
}
