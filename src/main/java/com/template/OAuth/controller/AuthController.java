package com.template.OAuth.controller;

import com.template.OAuth.annotation.Auditable;
import com.template.OAuth.config.AppProperties;
import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.dto.*;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.exception.ApiException;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.*;
import com.template.OAuth.util.CookieUtil;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication management endpoints")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final MessageService messageService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;

    @Autowired
    public AuthController(JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            AuditService auditService,
            MetricsService metricsService,
            MessageService messageService,
            AuthService authService,
            RefreshTokenService refreshTokenService,
            AppProperties appProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.messageService = messageService;
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.appProperties = appProperties;
    }

    @Operation(summary = "Register a new user with email and password", description = "Registers a new user account that requires email verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input data or email already in use", content = @Content)
    })
    @SecurityRequirements
    @PostMapping("/register")
    @Timed(value = "auth.register.time", description = "Time taken to register new user")
    public ResponseEntity<Map<String, String>> registerUser(
            @Parameter(description = "User registration details", required = true) @Valid @RequestBody EmailRegistrationRequest registrationRequest) {

        User user = authService.registerUser(registrationRequest);
        metricsService.incrementRegistration();

        Map<String, String> response = new HashMap<>();
        response.put("message", messageService.getMessage("user.created"));
        response.put("email", user.getEmail());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verify email address", description = "Verifies a user's email address using the token sent in the verification email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token", content = @Content)
    })
    @SecurityRequirements
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        boolean ok = authService.verifyEmail(token);
        if (ok) {
            String redirect = appProperties.getApplication().getBaseUrl() + "/login?verified=1";
            return ResponseEntity.status(302).header("Location", redirect).build();
        }
        Map<String, String> body = Map.of("message", messageService.getMessage("auth.token.invalid"));
        return ResponseEntity.badRequest().body(body);
    }

    @Operation(summary = "Resend verification email", description = "Resends the verification email for unverified accounts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification email sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid email or account already verified", content = @Content)
    })
    @SecurityRequirements
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerificationEmail(
            @Parameter(description = "Email address", required = true) @RequestParam String email) {

        authService.resendVerificationEmail(email);

        Map<String, String> response = new HashMap<>();
        response.put("message", messageService.getMessage("email.verification.resent"));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Initiate password reset", description = "Sends a password reset email to the specified email address")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset email sent successfully")
    })
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Parameter(description = "Password reset request", required = true) @Valid @RequestBody PasswordResetRequest resetRequest) {

        authService.initiatePasswordReset(resetRequest.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", messageService.getMessage("email.password.reset.sent"));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Reset password", description = "Resets the user's password using the token sent in the reset email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token", content = @Content)
    })
    @SecurityRequirements
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Parameter(description = "Password reset completion data", required = true) @Valid @RequestBody PasswordResetCompletion resetCompletion) {

        // resetPassword throws a typed ApiException on an invalid/expired token (handled by the
        // advice); a normal return is always success.
        authService.resetPassword(resetCompletion.getToken(), resetCompletion.getPassword());

        Map<String, String> response = new HashMap<>();
        response.put("message", messageService.getMessage("password.reset.success"));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Login with email and password", description = "Authenticates a user with email and password credentials")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid credentials or unverified account", content = @Content)
    })
    @SecurityRequirements
    @PostMapping("/email-login")
    @Timed(value = "auth.email.login.time", description = "Time taken to perform email login")
    public ResponseEntity<AuthResponse> loginWithEmail(
            @Parameter(description = "User login details", required = true) @Valid @RequestBody EmailLoginRequest loginRequest,
            HttpServletResponse response) {

        try {
            authService.authenticateAndGenerateTokens(loginRequest, response);
            metricsService.incrementAuthSuccess();
            return ResponseEntity.ok(new AuthResponse(null, messageService.getMessage("auth.login.success")));
        } catch (ApiException e) {
            metricsService.incrementAuthFailure();
            return ResponseEntity.status(e.getStatus())
                    .body(new AuthResponse(null, messageService.getMessage(e.getMessageKey())));
        }
    }

    @Operation(summary = "Get current user", description = "Retrieves the current authenticated user from the JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not authenticated", content = @Content)
    })
    @GetMapping("/user")
    @Timed(value = "auth.get-user.time", description = "Time taken to get current user")
    public ResponseEntity<?> getUserFromToken(HttpServletRequest request) {
        Optional<String> tokenOpt = jwtTokenProvider.getTokenFromRequest(request);

        if (tokenOpt.isPresent()) {
            String token = tokenOpt.get();
            if (jwtTokenProvider.validateToken(token)) {
                String email = jwtTokenProvider.getEmailFromToken(token);
                Optional<User> user = userRepository.findByEmail(email);

                if (user.isPresent()) {
                    return ResponseEntity.ok(user.get());
                }
            }
        }

        metricsService.incrementError("unauthorized");

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", messageService.getMessage("auth.access.denied"));
        return ResponseEntity.status(401).body(errorResponse);
    }

    @Operation(summary = "Logout", description = "Logs out the current user by clearing auth cookies")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully logged out"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not authenticated", content = @Content)
    })
    @PostMapping("/logout")
    @Auditable(type = AuditEventType.LOGOUT, description = "User logout")
    @Timed(value = "auth.logout.time", description = "Time taken to logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        // Extract current user (if JWT present); logout should still clear cookies even
        // if not
        String userEmail = "anonymous";
        Optional<String> tokenOpt = jwtTokenProvider.getTokenFromRequest(request);
        if (tokenOpt.isPresent()) {
            try {
                userEmail = jwtTokenProvider.getEmailFromToken(tokenOpt.get());
            } catch (Exception ignored) {
            }
        }

        // Clear cookies (must match exact paths used when setting them)
        CookieUtil.addCookie(response, "jwt", "", "/", 0, appProperties);
        CookieUtil.addCookie(response, "refresh_token", "", "/refresh-token", 0, appProperties);

        // Revoke refresh tokens by USER
        if (!"anonymous".equals(userEmail)) {
            refreshTokenService.revokeAllForUser(userEmail);
            metricsService.incrementLogout();
            auditService.logEvent(AuditEventType.LOGOUT,
                    messageService.getMessage("auth.logout.success"),
                    "Email: " + userEmail);
        }

        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("message", messageService.getMessage("auth.logout.success"));
        return ResponseEntity.ok(responseMap);
    }

    @Operation(summary = "Get login URL", description = "Returns the URL for OAuth2 authentication")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login URL retrieved successfully")
    })
    @SecurityRequirements
    @GetMapping("/login-url")
    public ResponseEntity<String> getLoginUrl() {
        return ResponseEntity.ok("/oauth2/authorization/google");
    }
}
