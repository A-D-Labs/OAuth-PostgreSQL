package com.template.OAuth.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import java.io.File;

@Configuration
@Profile("!test")
@Order(0) // Make this run as early as possible
public class EnvConfig {

    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);

    @Autowired
    private Environment springEnv;

    // Load environment variables from .env file early in the startup process
    static {
        try {
            String activeProfile = System.getProperty("spring.profiles.active", "dev");
            String envFile = ".env." + activeProfile;

            // Check if profile-specific .env file exists
            File profileEnvFile = new File(envFile);
            if (!profileEnvFile.exists()) {
                envFile = ".env"; // Fall back to default .env
            }

            logger.info("Static initializer: Loading environment variables from: {}", envFile);

            // Load the environment file
            Dotenv dotenv = Dotenv.configure()
                    .filename(envFile)
                    .ignoreIfMissing()
                    .load();

            // Set critical environment variables
            setEnvVar("DB_URL", dotenv.get("DB_URL"));
            setEnvVar("DB_USERNAME", dotenv.get("DB_USERNAME"));
            setEnvVar("DB_PASSWORD", dotenv.get("DB_PASSWORD"));
            setEnvVar("JWT_SECRET", dotenv.get("JWT_SECRET"));

            // Other OAuth2 environment variables
            setEnvVar("GOOGLE_CLIENT_ID", dotenv.get("GOOGLE_CLIENT_ID"));
            setEnvVar("GOOGLE_CLIENT_SECRET", dotenv.get("GOOGLE_CLIENT_SECRET"));
            setEnvVar("MICROSOFT_CLIENT_ID", dotenv.get("MICROSOFT_CLIENT_ID"));
            setEnvVar("MICROSOFT_CLIENT_SECRET", dotenv.get("MICROSOFT_CLIENT_SECRET"));

            // Email configuration
            setEnvVar("EMAIL_HOST", dotenv.get("EMAIL_HOST", "smtp.gmail.com"));
            setEnvVar("EMAIL_PORT", dotenv.get("EMAIL_PORT", "587"));
            setEnvVar("EMAIL_USERNAME", dotenv.get("EMAIL_USERNAME"));
            setEnvVar("EMAIL_PASSWORD", dotenv.get("EMAIL_PASSWORD"));
            setEnvVar("EMAIL_FROM_ADDRESS", dotenv.get("EMAIL_FROM_ADDRESS", "no-reply@example.com"));
            setEnvVar("EMAIL_FROM_NAME", dotenv.get("EMAIL_FROM_NAME", "OAuth Template App"));

            // Application configuration
            setEnvVar("APP_BASE_URL", dotenv.get("APP_BASE_URL", "http://localhost:3000"));
            setEnvVar("SUPPORT_EMAIL", dotenv.get("SUPPORT_EMAIL", "support@example.com"));
            setEnvVar("APP_NAME", dotenv.get("APP_NAME", "OAuth Template"));
            setEnvVar("FRONTEND_URL", dotenv.get("FRONTEND_URL", "http://localhost:3000"));
            setEnvVar("LOGIN_SUCCESS_REDIRECT_URL", dotenv.get("LOGIN_SUCCESS_REDIRECT_URL", "/home"));
            setEnvVar("ADMIN_EMAILS", dotenv.get("ADMIN_EMAILS", "admin@example.com"));

            logger.info("Static initializer: Environment variables loaded successfully for profile: {}", activeProfile);
        } catch (Exception e) {
            logger.error("Static initializer: Failed to load environment variables", e);
        }
    }

    private static void setEnvVar(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        }
    }

    @PostConstruct
    public void logActiveProfile() {
        String[] activeProfiles = springEnv.getActiveProfiles();
        if (activeProfiles.length == 0) {
            logger.info("No active profile set, using default profile");
        } else {
            for (String profile : activeProfiles) {
                logger.info("Active profile: {}", profile);
            }
        }

        // Log key application settings
        logger.info("Database URL: {}", System.getProperty("DB_URL"));
        logger.info("Application name: {}", System.getProperty("APP_NAME"));
        logger.info("Frontend URL: {}", System.getProperty("FRONTEND_URL"));
    }
}