package com.template.OAuth.config;

import com.template.OAuth.security.JwtAuthenticationToken;
import com.template.OAuth.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter { // ✅ Changed superclass

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = jwtTokenProvider.getTokenFromRequest(request).orElse(null);

        if (token != null && jwtTokenProvider.validateToken(token)
                && !jwtTokenProvider.isMfaChallengeToken(token)) {
            String email = jwtTokenProvider.getEmailFromToken(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (userDetails != null && !isRevoked(token, userDetails)) {
                    JwtAuthenticationToken authentication =
                            new JwtAuthenticationToken(userDetails, token, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * True if this access token was issued before the user's revocation epoch
     * ({@code tokens_invalid_before}) — i.e. an admin ban / forced-logout has invalidated it.
     * No extra query: the epoch rides on the loaded {@link UserPrincipal}.
     */
    private boolean isRevoked(String token, UserDetails userDetails) {
        if (!(userDetails instanceof UserPrincipal principal)) {
            return false;
        }
        Instant epoch = principal.getTokensInvalidBefore();
        if (epoch == null) {
            return false;
        }
        Instant issuedAt = jwtTokenProvider.getIssuedAt(token);
        return issuedAt != null && issuedAt.isBefore(epoch);
    }
}
