package com.template.OAuth.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hash of the opaque refresh token. The raw token is never stored. */
    @Column(unique = true, nullable = false)
    private String token;

    /**
     * The raw (unhashed) token, held only in memory for the request that creates or rotates
     * it so it can be written to the client cookie. Never persisted.
     */
    @Transient
    private String rawToken;

    /**
     * SHA-256 hash of the token most recently rotated away. If a presented token matches this
     * (rather than {@link #token}), it has already been consumed — a reuse signal.
     */
    @Column(name = "previous_token")
    private String previousToken;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    private Instant expiryDate;
}
