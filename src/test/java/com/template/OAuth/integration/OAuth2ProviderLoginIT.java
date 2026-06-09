package com.template.OAuth.integration;

import com.template.OAuth.BaseIntegrationTest;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuthProvider;
import com.template.OAuth.enums.Role;
import com.template.OAuth.repositories.UserRepository;
import com.template.OAuth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the heart of OAuth provider login — {@link UserService#saveUser} — for every
 * supported provider, against the real PostgreSQL + Flyway schema. This is the flow the
 * OAuth2SuccessHandler / OAuth2UserService delegate to once a provider has authenticated
 * the user, and it had zero coverage before. Running it on Postgres also validates that
 * the AuthProvider enum column and the provider-id columns round-trip through the rewritten
 * V1 schema.
 */
@Transactional
class OAuth2ProviderLoginIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    static Stream<Arguments> providers() {
        return Stream.of(
                Arguments.of("https://accounts.google.com", AuthProvider.GOOGLE),
                Arguments.of("spotify", AuthProvider.SPOTIFY),
                Arguments.of("apple", AuthProvider.APPLE),
                Arguments.of("soundcloud", AuthProvider.SOUNDCLOUD));
    }

    @ParameterizedTest
    @MethodSource("providers")
    void firstLoginCreatesEnabledUserMappedToProvider(String providerName, AuthProvider expected) {
        String email = expected.name().toLowerCase() + "-user@example.com";
        String providerId = "pid-" + expected.name();
        OidcUser oidcUser = oidcUser(email, "Test " + expected.name(), "https://pic/" + expected.name());

        User saved = userService.saveUser(oidcUser, providerName, providerId);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getPrimaryProvider()).isEqualTo(expected);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getRoles()).contains(Role.USER);
        assertThat(saved.getLoginCount()).isEqualTo(1);
        assertThat(userService.findByProviderId(expected, providerId)).isPresent();
    }

    @ParameterizedTest
    @MethodSource("providers")
    void repeatedLoginUpdatesInPlaceAndIncrementsLoginCount(String providerName, AuthProvider expected) {
        String email = expected.name().toLowerCase() + "-repeat@example.com";
        String providerId = "pid-repeat-" + expected.name();

        userService.saveUser(oidcUser(email, "First Name", "https://pic/1"), providerName, providerId);
        User second = userService.saveUser(oidcUser(email, "Updated Name", "https://pic/2"), providerName, providerId);

        assertThat(userRepository.findByEmail(email)).isPresent();
        assertThat(userRepository.findAll().stream().filter(u -> email.equals(u.getEmail()))).hasSize(1);
        assertThat(second.getName()).isEqualTo("Updated Name");
        assertThat(second.getLoginCount()).isEqualTo(2);
    }

    private OidcUser oidcUser(String email, String fullName, String picture) {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(email);
        when(oidcUser.getFullName()).thenReturn(fullName);
        when(oidcUser.getPicture()).thenReturn(picture);
        return oidcUser;
    }
}
