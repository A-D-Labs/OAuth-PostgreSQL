package com.template.OAuth.repositories;

import com.template.OAuth.entities.MfaRecoveryCode;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuthProvider;
import com.template.OAuth.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MfaModelRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MfaRecoveryCodeRepository recoveryCodeRepository;

    private User newUser(String email) {
        User user = new User();
        user.setName("MFA User");
        user.setEmail(email);
        user.setPrimaryProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        user.addRole(Role.USER);
        return user;
    }

    @Test
    void persistsMfaFieldsOnUser() {
        User user = newUser("mfa-fields@example.com");
        user.setMfaEnabled(true);
        user.setTotpSecret("JBSWY3DPEHPK3PXP");
        userRepository.saveAndFlush(user);

        User found = userRepository.findByEmail("mfa-fields@example.com").orElseThrow();
        assertThat(found.isMfaEnabled()).isTrue();
        assertThat(found.getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void storesAndConsumesRecoveryCodes() {
        User user = userRepository.saveAndFlush(newUser("mfa-codes@example.com"));

        MfaRecoveryCode code = new MfaRecoveryCode();
        code.setUser(user);
        code.setCodeHash("hash-of-recovery-code");
        recoveryCodeRepository.saveAndFlush(code);

        // Lookup by hash (how a presented recovery code is verified)
        Optional<MfaRecoveryCode> byHash = recoveryCodeRepository.findByCodeHash("hash-of-recovery-code");
        assertThat(byHash).isPresent();
        assertThat(byHash.get().getUsedAt()).isNull();

        // Mark used (single-use semantics)
        byHash.get().setUsedAt(Instant.now());
        recoveryCodeRepository.saveAndFlush(byHash.get());

        List<MfaRecoveryCode> forUser = recoveryCodeRepository.findByUser(user);
        assertThat(forUser).hasSize(1);
        assertThat(forUser.get(0).getUsedAt()).isNotNull();
    }
}
