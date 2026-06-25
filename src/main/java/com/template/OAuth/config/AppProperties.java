package com.template.OAuth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Security security = new Security();
    private Profile profile = new Profile();
    private Cors cors = new Cors();
    private Email email = new Email();
    private Application application = new Application();

    @Getter
    @Setter
    public static class Security {
        private Cookie cookie = new Cookie();
        private Jwt jwt = new Jwt();
        private Refresh refresh = new Refresh();
        private RateLimiting rateLimiting = new RateLimiting();
        private Verification verification = new Verification();
        private PasswordReset passwordReset = new PasswordReset();

        /**
         * Whether the app sits behind a trusted reverse proxy/load balancer that sets a
         * correct X-Forwarded-For header. Leave false unless every request is guaranteed to
         * pass through such a proxy: when false, the client's real socket address is used for
         * rate limiting, so a client cannot spoof XFF to dodge throttling. Set true only in
         * environments where an upstream proxy strips and rewrites XFF.
         */
        private boolean trustProxy = false;

        @Getter
        @Setter
        public static class Cookie {
            /**
             * When true, sets the 'Secure' attribute on cookies so they are only sent over HTTPS.
             * Use true in staging/prod (HTTPS), false for local http://localhost dev.
             */
            private boolean secure = false;

            /**
             * SameSite policy for cookies. Valid values: "Lax", "Strict", "None".
             * Use "None" ONLY when you need cross-site cookies (e.g., frontend on a different domain),
             * and only together with secure=true (HTTPS), otherwise browsers will drop the cookie.
             */
            private String sameSite = "Lax";

            /**
             * Cookie domain, e.g. ".yourdomain.com" to share across subdomains.
             * Leave null/empty for localhost.
             */
            private String domain;

            /**
             * Optional: send the 'Partitioned' attribute (CHIPS) together with SameSite=None.
             * Only enable if you explicitly need it and understand the implications.
             */
            private boolean partitioned = false;
        }

        @Getter
        @Setter
        public static class Jwt {
            private long expiration = 3600000; // 1 hour in milliseconds
        }

        @Getter
        @Setter
        public static class Refresh {
            private long expiration = 604800000; // 7 days in milliseconds (idle timeout)
            // Absolute ceiling on a refresh family's age regardless of rotation; past this the
            // user must re-authenticate. Default 30 days.
            private long absoluteExpiration = 2592000000L; // 30 days in milliseconds
        }

        @Getter
        @Setter
        public static class RateLimiting {
            private boolean enabled = true;
            private int defaultLimit = 100;
            private int authLimit = 10;
            private int sensitiveLimit = 3;
            private int blockDurationMinutes = 30;
            private int maxFailedAttempts = 5;
        }

        @Getter
        @Setter
        public static class Verification {
            private int expirationHours = 24;
        }

        @Getter
        @Setter
        public static class PasswordReset {
            private int expirationHours = 1;
        }
    }

    @Getter
    @Setter
    public static class Profile {
        private String uploadDir = "uploads/profiles";
        private int maxBioLength = 500;
        private String defaultTheme = "SYSTEM";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:3000");
    }

    @Getter
    @Setter
    public static class Email {
        private String host = "smtp.gmail.com";
        private int port = 587;
        private String username;
        private String password;
        private String fromAddress;
        private String fromName = "OAuth Template App";
        private boolean debug = false;
    }

    @Getter
    @Setter
    public static class Application {
        private String baseUrl = "http://localhost:3000";
        private String supportEmail = "support@example.com";
        private String name = "OAuth Template";
        private String verifyBaseUrl = "http://localhost:8080";
    }
}
