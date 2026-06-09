package com.template.OAuth.repositories;

import com.template.OAuth.entities.RefreshToken;
import com.template.OAuth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByPreviousToken(String previousToken);
    Optional<RefreshToken> findByUser(User user);

    // Keep the single delete (works fine)
    void deleteByUser(User user);

    // And also offer a plural variant (no harm; sometimes clearer in service code)
    void deleteAllByUser(User user);
}
