package com.template.OAuth.repositories;

import com.template.OAuth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // For OAuth provider IDs
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByMicrosoftId(String microsoftId);

    // For email verification
    Optional<User> findByVerificationToken(String token);

    // For password reset
    Optional<User> findByPasswordResetToken(String token);
}