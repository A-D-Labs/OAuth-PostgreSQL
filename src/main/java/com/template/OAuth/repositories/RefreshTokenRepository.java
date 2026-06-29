package com.template.OAuth.repositories;

import com.template.OAuth.entities.RefreshToken;
import com.template.OAuth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByPreviousToken(String previousToken);

    // Multi-device (ADR-0007): a user now has many sessions, not one.
    List<RefreshToken> findByUser(User user);
    Optional<RefreshToken> findBySessionId(String sessionId);

    void deleteByUser(User user);
    void deleteAllByUser(User user);
    void deleteBySessionId(String sessionId);
}
