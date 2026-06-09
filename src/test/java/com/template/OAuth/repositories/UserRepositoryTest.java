package com.template.OAuth.repositories;

import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuthProvider;
import com.template.OAuth.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindByEmail() {
        // Arrange
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPrimaryProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        user.addRole(Role.USER);

        userRepository.saveAndFlush(user);

        // Act
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testFindByGoogleId() {
        // Arrange
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPrimaryProvider(AuthProvider.GOOGLE);
        user.setGoogleId("google-123");
        user.setEnabled(true);
        user.addRole(Role.USER);

        userRepository.saveAndFlush(user);

        // Act
        Optional<User> found = userRepository.findByGoogleId("google-123");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("google-123", found.get().getGoogleId());
    }

    @Test
    void testFindByVerificationToken() {
        // Arrange
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPrimaryProvider(AuthProvider.LOCAL);
        user.setEnabled(false);
        user.setVerificationToken("verification-token");
        user.setVerificationTokenExpiry(Instant.now().plusSeconds(3600));
        user.addRole(Role.USER);

        userRepository.saveAndFlush(user);

        // Act
        Optional<User> found = userRepository.findByVerificationToken("verification-token");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("verification-token", found.get().getVerificationToken());
    }

    @Test
    void testFindByPasswordResetToken() {
        // Arrange
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPrimaryProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        user.setPasswordResetToken("reset-token");
        user.setPasswordResetTokenExpiry(Instant.now().plusSeconds(3600));
        user.addRole(Role.USER);

        userRepository.saveAndFlush(user);

        // Act
        Optional<User> found = userRepository.findByPasswordResetToken("reset-token");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("reset-token", found.get().getPasswordResetToken());
    }
}