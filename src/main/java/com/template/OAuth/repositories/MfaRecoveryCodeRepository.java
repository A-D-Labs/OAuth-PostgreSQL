package com.template.OAuth.repositories;

import com.template.OAuth.entities.MfaRecoveryCode;
import com.template.OAuth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    Optional<MfaRecoveryCode> findByCodeHash(String codeHash);

    List<MfaRecoveryCode> findByUser(User user);

    void deleteAllByUser(User user);
}
