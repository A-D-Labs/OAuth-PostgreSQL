package com.template.OAuth.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-time MFA recovery code, hashed at rest (SHA-256). Lets a user pass the MFA
 * challenge when their authenticator is unavailable. Single-use: {@link #usedAt} is
 * stamped when consumed and a used code is never accepted again.
 */
@Getter
@Setter
@Entity
@Table(name = "mfa_recovery_codes")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    /** SHA-256 hash of the raw recovery code. The raw code is shown once, never stored. */
    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    /** When the code was consumed; null while still usable. */
    @Column(name = "used_at")
    private Instant usedAt;
}
