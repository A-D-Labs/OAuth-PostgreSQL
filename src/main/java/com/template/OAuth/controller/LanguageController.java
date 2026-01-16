package com.template.OAuth.controller;

import com.template.OAuth.entities.User;
import com.template.OAuth.service.MessageService;
import com.template.OAuth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/language")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class LanguageController {

    private final LocaleResolver localeResolver;
    private final MessageService messageService;
    private final UserService userService;

    @Autowired
    public LanguageController(LocaleResolver localeResolver, MessageService messageService, UserService userService) {
        this.localeResolver = localeResolver;
        this.messageService = messageService;
        this.userService = userService;
    }

    @Operation(summary = "Change language", description = "Changes the user's language preference and updates their profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Language changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid language code")
    })
    @PostMapping("/change")
    public ResponseEntity<Map<String, String>> changeLanguage(
            @Parameter(description = "Language code (e.g., en, fr, es, de)", required = true) @RequestParam String lang,
            @NonNull HttpServletRequest request,
            HttpServletResponse response) {

        // Validate language code
        if (!isValidLanguage(lang)) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid language code");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Parse the locale from the language parameter
        Locale locale = Locale.forLanguageTag(lang);

        // Set the new locale
        localeResolver.setLocale(request, response, locale);

        // Update the user's profile language preference if authenticated
        try {
            User currentUser = userService.getCurrentUser();
            if (!lang.equals(currentUser.getLanguagePreference())) {
                currentUser.setLanguagePreference(lang);
                userService.saveUser(currentUser);
            }
        } catch (Exception e) {
            // User might not be authenticated, just continue
        }

        // Return a success message in the new language
        Map<String, String> result = new HashMap<>();
        result.put("message", messageService.getMessage("language.changed"));
        result.put("language", locale.getDisplayLanguage(locale));

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get available languages", description = "Retrieves list of available languages")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Languages retrieved successfully")
    })
    @GetMapping("/available")
    public ResponseEntity<Map<String, String>> getAvailableLanguages() {
        Map<String, String> languages = new HashMap<>();

        // Add supported languages
        languages.put("en", "English");
        languages.put("es", "Español");
        languages.put("fr", "Français");
        languages.put("de", "Deutsch");

        return ResponseEntity.ok(languages);
    }

    @Operation(summary = "Get current language", description = "Retrieves the user's current language")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Current language retrieved successfully")
    })
    @GetMapping("/current")
    public ResponseEntity<Map<String, String>> getCurrentLanguage(@NonNull HttpServletRequest request) {
        // Get the current locale
        Locale currentLocale = localeResolver.resolveLocale(request);

        Map<String, String> result = new HashMap<>();
        result.put("language", currentLocale.getLanguage());
        result.put("displayLanguage", currentLocale.getDisplayLanguage(currentLocale));

        return ResponseEntity.ok(result);
    }

    // Helper method to validate language codes
    private boolean isValidLanguage(String lang) {
        return lang != null && (lang.equals("en") || lang.equals("es") ||
                lang.equals("fr") || lang.equals("de"));
    }
}