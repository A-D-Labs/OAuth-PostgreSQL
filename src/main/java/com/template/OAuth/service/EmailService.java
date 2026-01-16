package com.template.OAuth.service;

import com.template.OAuth.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.util.Objects;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;
    private final MessageService messageService;

    @Autowired
    public EmailService(JavaMailSender mailSender,
            TemplateEngine templateEngine,
            AppProperties appProperties,
            MessageService messageService) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.appProperties = appProperties;
        this.messageService = messageService;
    }

    @Async
    public void sendVerificationEmail(String to, String name, String token) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("name", name);
            String verifyUrl = appProperties.getApplication().getVerifyBaseUrl()
                    + "/auth/verify-email?token=" + token;
            context.setVariable("verificationUrl", verifyUrl);

            context.setVariable("expirationHours", appProperties.getSecurity().getVerification().getExpirationHours());
            context.setVariable("supportEmail", appProperties.getApplication().getSupportEmail());

            String emailContent = templateEngine.process("mail/email-verification", context);

            helper.setTo(Objects.requireNonNull(to));
            helper.setSubject(messageService.getMessage("email.verification.subject"));
            helper.setText(Objects.requireNonNull(emailContent), true);
            helper.setFrom(Objects.requireNonNull(appProperties.getEmail().getFromAddress()),
                    Objects.requireNonNull(appProperties.getEmail().getFromName()));

            mailSender.send(mimeMessage);

            logger.info("Verification email sent to: {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("Failed to send verification email to: {}", to, e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String name, String token) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("resetUrl",
                    appProperties.getApplication().getBaseUrl() + "/reset-password?token=" + token);
            context.setVariable("expirationHours", appProperties.getSecurity().getPasswordReset().getExpirationHours());
            context.setVariable("supportEmail", appProperties.getApplication().getSupportEmail());

            String emailContent = templateEngine.process("mail/password-reset", context);

            helper.setTo(Objects.requireNonNull(to));
            helper.setSubject(messageService.getMessage("email.password.reset.subject"));
            helper.setText(Objects.requireNonNull(emailContent), true);
            helper.setFrom(Objects.requireNonNull(appProperties.getEmail().getFromAddress()),
                    Objects.requireNonNull(appProperties.getEmail().getFromName()));

            mailSender.send(mimeMessage);

            logger.info("Password reset email sent to: {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("Failed to send password reset email to: {}", to, e);
        }
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("loginUrl", appProperties.getApplication().getBaseUrl() + "/login");
            context.setVariable("supportEmail", appProperties.getApplication().getSupportEmail());

            String emailContent = templateEngine.process("mail/welcome-email", context);

            helper.setTo(Objects.requireNonNull(to));
            helper.setSubject(messageService.getMessage("email.welcome.subject"));
            helper.setText(Objects.requireNonNull(emailContent), true);
            helper.setFrom(Objects.requireNonNull(appProperties.getEmail().getFromAddress()),
                    Objects.requireNonNull(appProperties.getEmail().getFromName()));

            mailSender.send(mimeMessage);

            logger.info("Welcome email sent to: {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("Failed to send welcome email to: {}", to, e);
        }
    }
}