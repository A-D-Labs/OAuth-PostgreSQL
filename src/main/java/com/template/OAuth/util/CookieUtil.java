package com.template.OAuth.util;

import com.template.OAuth.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import java.util.Objects;

public class CookieUtil {

    public static void addCookie(HttpServletResponse response,
            String name,
            String value,
            String path,
            long maxAgeSeconds,
            AppProperties app) {

        var cfg = app.getSecurity().getCookie();

        ResponseCookie.ResponseCookieBuilder b = ResponseCookie
                .from(Objects.requireNonNull(name), Objects.requireNonNull(value))
                .httpOnly(true) // protect against XSS
                .secure(cfg.isSecure()) // HTTPS in prod
                .path(path == null ? "/" : path) // cookie path
                .maxAge(maxAgeSeconds);

        if (cfg.getDomain() != null && !cfg.getDomain().isBlank()) {
            b.domain(cfg.getDomain()); // share across subdomains if needed
        }

        // SameSite
        String sameSite = cfg.getSameSite();
        if (sameSite != null) {
            switch (sameSite.toLowerCase()) {
                case "strict":
                    b.sameSite("Strict");
                    break;
                case "none":
                    b.sameSite("None");
                    break;
                default:
                    b.sameSite("Lax");
                    break;
            }
        }

        // Optional: CHIPS Partitioned (only valid with SameSite=None)
        if (cfg.isPartitioned() && "none".equalsIgnoreCase(sameSite)) {
            // ResponseCookie doesn't support Partitioned yet -> append manually
            response.addHeader("Set-Cookie", b.build().toString() + "; Partitioned");
            return;
        }

        response.addHeader("Set-Cookie", b.build().toString());
    }

    public static void clearCookie(HttpServletResponse response, String name, AppProperties app) {
        // Clear with Max-Age=0
        addCookie(response, name, "", "/", 0, app);
    }
}
