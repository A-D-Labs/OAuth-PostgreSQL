package com.template.OAuth.security;

import com.template.OAuth.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class UserPrincipal implements UserDetails {
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    /** Carried so the JWT filter can reject tokens issued before this instant — no extra query. */
    private final Instant tokensInvalidBefore;

    public UserPrincipal(String email, String password, boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this(email, password, enabled, authorities, null);
    }

    public UserPrincipal(String email, String password, boolean enabled,
                         Collection<? extends GrantedAuthority> authorities, Instant tokensInvalidBefore) {
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
        this.tokensInvalidBefore = tokensInvalidBefore;
    }

    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());

        return new UserPrincipal(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                authorities,
                user.getTokensInvalidBefore()
        );
    }

    /** Instant before which this user's access tokens are revoked; null if never revoked. */
    public Instant getTokensInvalidBefore() {
        return tokensInvalidBefore;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}