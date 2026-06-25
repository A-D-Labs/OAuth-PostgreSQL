package com.template.OAuth.service;

import com.template.OAuth.config.AppProperties;
import com.template.OAuth.config.JwtTokenProvider;
import com.template.OAuth.dto.EmailLoginRequest;
import com.template.OAuth.dto.EmailRegistrationRequest;
import com.template.OAuth.entities.RefreshToken;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuthProvider;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.enums.NotificationType;
import com.template.OAuth.enums.Role;
import com.template.OAuth.exception.AccountDisabledException;
import com.template.OAuth.exception.InvalidCredentialsException;
import com.template.OAuth.exception.InvalidTokenException;
import com.template.OAuth.exception.TokenExpiredException;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.security.UserPrincipal;
import com.template.OAuth.util.CookieUtil;
import com.template.OAuth.util.TokenHasher;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final AuthenticationManager authenticationManager;
    private final AppProperties appProperties;
    private final SessionIssuer sessionIssuer;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       EmailService emailService,
                       RefreshTokenService refreshTokenService,
                       AuditService auditService,
                       AuthenticationManager authenticationManager,
                       AppProperties appProperties,
                       SessionIssuer sessionIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.authenticationManager = authenticationManager;
        this.appProperties = appProperties;
        this.sessionIssuer = sessionIssuer;
    }

    @Transactional
    public User registerUser(EmailRegistrationRequest registrationRequest) {
        // Anti-enumeration: if the email is already taken, return the existing user without
        // touching it or sending mail. The caller responds identically to a fresh signup, so
        // an attacker cannot tell registered emails apart from unregistered ones.
        Optional<User> existing = userRepository.findByEmail(registrationRequest.getEmail());
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new user
        User user = new User();
        user.setName(registrationRequest.getName());
        user.setEmail(registrationRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registrationRequest.getPassword()));
        user.setPrimaryProvider(AuthProvider.LOCAL);
        user.setEnabled(false); // Not enabled until email verification

        // Generate verification token; store only its hash, email the raw value.
        String token = generateToken();
        user.setVerificationToken(TokenHasher.sha256Hex(token));
        user.setVerificationTokenExpiry(Instant.now().plusSeconds(
                appProperties.getSecurity().getVerification().getExpirationHours() * 3600L));

        // Assign default USER role
        user.addRole(Role.USER);

        // Default notification preferences
        user.enableNotification(NotificationType.EMAIL_SECURITY);
        user.enableNotification(NotificationType.IN_APP_GENERAL);

        // Save user
        user = userRepository.save(user);

        // Send verification email
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), token);

        // Log the registration event
        auditService.logEvent(
                AuditEventType.USER_CREATED,
                "New user registered with email authentication",
                "User: " + user.getEmail()
        );

        return user;
    }

    @Transactional
public boolean verifyEmail(String token) {
    var opt = userRepository.findByVerificationToken(TokenHasher.sha256Hex(token));
    if (opt.isEmpty()) {
        // Token not found — either already used (user enabled & token cleared) or invalid
        // If you want to be extra nice, you can return false here and let controller decide the message.
        return false;
    }

    User user = opt.get();
    if (user.isEnabled()) {
        // Idempotent: treat as success
        return true;
    }

    if (user.getVerificationTokenExpiry() != null &&
        user.getVerificationTokenExpiry().isBefore(Instant.now())) {
        return false;
    }

    user.setEnabled(true);
    user.setVerificationToken(null);
    user.setVerificationTokenExpiry(null);
    userRepository.save(user);

    emailService.sendWelcomeEmail(user.getEmail(), user.getName());
    auditService.logEvent(
            AuditEventType.USER_UPDATED,
            "User email verified",
            "User: " + user.getEmail()
    );
    return true;
}


    @Transactional
    public void resendVerificationEmail(String email) {
        // Anti-enumeration: silently succeed for unknown or already-verified accounts so the
        // response can't be used to probe which emails exist or are verified.
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        if (user.isEnabled()) {
            return;
        }

        // Generate new verification token; store only its hash, email the raw value.
        String token = generateToken();
        user.setVerificationToken(TokenHasher.sha256Hex(token));
        user.setVerificationTokenExpiry(Instant.now().plusSeconds(
                appProperties.getSecurity().getVerification().getExpirationHours() * 3600L));

        userRepository.save(user);

        // Send verification email
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), token);

        // Log the event
        auditService.logEvent(
                AuditEventType.SYSTEM_EVENT,
                "Verification email resent",
                "User: " + user.getEmail()
        );
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        // Even if user doesn't exist, don't reveal that to potential attackers
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Generate password reset token; store only its hash, email the raw value.
            String token = generateToken();
            user.setPasswordResetToken(TokenHasher.sha256Hex(token));
            user.setPasswordResetTokenExpiry(Instant.now().plusSeconds(
                    appProperties.getSecurity().getPasswordReset().getExpirationHours() * 3600L));

            userRepository.save(user);

            // Send password reset email
            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), token);

            // Log the event
            auditService.logEvent(
                    AuditEventType.SYSTEM_EVENT,
                    "Password reset initiated",
                    "User: " + user.getEmail()
            );
        }
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(TokenHasher.sha256Hex(token))
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));

        // Check if token is expired
        if (user.getPasswordResetTokenExpiry() != null &&
            user.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw new TokenExpiredException("Password reset token has expired");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);

        userRepository.save(user);

        // Log the event
        auditService.logEvent(
                AuditEventType.USER_UPDATED,
                "User password reset",
                "User: " + user.getEmail()
        );

        return true;
    }

    /**
     * Authenticate email+password. If the account has MFA active, issue only an MFA challenge
     * (no session) and return {@code true}; otherwise issue a full session and return {@code false}.
     */
    @Transactional
    public boolean authenticateAndGenerateTokens(EmailLoginRequest loginRequest, HttpServletResponse response) {
        try {
            // Authenticate via Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            // Get user from authentication
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findByEmail(userPrincipal.getUsername())
                    .orElseThrow(() -> new IllegalStateException(
                            "Authenticated principal has no backing user row"));

            // Record login
            user.recordLogin();
            userRepository.save(user);

            // Issue a session, or an MFA challenge if MFA is active.
            boolean mfaRequired = sessionIssuer.issueSessionOrChallenge(user, response);

            auditService.logEvent(
                    AuditEventType.LOGIN_SUCCESS,
                    mfaRequired ? "Password accepted; MFA challenge issued" : "User logged in with email and password",
                    "User: " + user.getEmail()
            );
            return mfaRequired;

        } catch (DisabledException e) {
            auditService.logEvent(
                    AuditEventType.LOGIN_FAILURE,
                    "Login attempt with disabled account",
                    "Email: " + loginRequest.getEmail()
            );
            throw new AccountDisabledException("Account is disabled; email not verified");
        } catch (BadCredentialsException e) {
            auditService.logEvent(
                    AuditEventType.LOGIN_FAILURE,
                    "Login attempt with invalid credentials",
                    "Email: " + loginRequest.getEmail()
            );
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }

    // Utility: secure random token
    private String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
