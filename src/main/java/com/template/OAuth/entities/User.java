package com.template.OAuth.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.template.OAuth.enums.AuthProvider;
import com.template.OAuth.enums.NotificationType;
import com.template.OAuth.enums.Role;
import com.template.OAuth.enums.ThemePreference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String picture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider primaryProvider;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    // Password authentication fields
    @Column(length = 60)
    private String password;

    // MFA / TOTP (Tier 2a). totpSecret is recoverable (needed to verify codes);
    // recovery codes are hashed and live in mfa_recovery_codes.
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(length = 64)
    private String verificationToken;

    private boolean enabled = false;

    @Column
    private Instant verificationTokenExpiry;

    @Column(length = 64)
    private String passwordResetToken;

    @Column
    private Instant passwordResetTokenExpiry;

    // OAuth provider IDs
    @Column(unique = true)
    private String googleId;

    @Column(unique = true)
    private String spotifyId;

    @Column(unique = true)
    private String appleId;

    @Column(unique = true)
    private String soundcloudId;

    // Extended profile information
    private String biography;
    private String location;
    private String phoneNumber;
    private String alternativeEmail;

    // Language preference for internationalization
    @Column(length = 10)
    private String languagePreference = "en"; // Default to English

    // User preferences
    @Enumerated(EnumType.STRING)
    private ThemePreference themePreference = ThemePreference.SYSTEM;

    // Notification preferences
    @ElementCollection
    @CollectionTable(name = "user_notification_preferences", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    private Set<NotificationType> enabledNotifications = new HashSet<>();

    // Activity tracking
    @CreationTimestamp
    private Instant registrationDate;

    @UpdateTimestamp
    private Instant lastUpdated;

    private Instant lastLoginDate;
    private Instant lastActiveDate;
    private Integer loginCount = 0;
    private Integer profileUpdateCount = 0;

    // Profile update history (One-to-Many relationship)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProfileUpdateHistory> updateHistory = new ArrayList<>();

    // Helper methods for role management
    public void addRole(Role role) {
        if (roles == null) {
            roles = new HashSet<>();
        }
        roles.add(role);
    }

    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }

    // Helper methods for notification preferences
    public void enableNotification(NotificationType type) {
        if (enabledNotifications == null) {
            enabledNotifications = new HashSet<>();
        }
        enabledNotifications.add(type);
    }

    public void disableNotification(NotificationType type) {
        if (enabledNotifications != null) {
            enabledNotifications.remove(type);
        }
    }

    public boolean hasNotificationEnabled(NotificationType type) {
        return enabledNotifications != null && enabledNotifications.contains(type);
    }

    // Helper method to record login
    public void recordLogin() {
        this.lastLoginDate = Instant.now();
        this.lastActiveDate = Instant.now();
        this.loginCount = (this.loginCount == null) ? 1 : this.loginCount + 1;
    }

    // Helper method to record activity
    public void recordActivity() {
        this.lastActiveDate = Instant.now();
    }

    // Helper method to add profile update history
    public void addUpdateHistory(String field, String oldValue, String newValue) {
        ProfileUpdateHistory history = new ProfileUpdateHistory();
        history.setUser(this);
        history.setFieldName(field);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setUpdateDate(Instant.now());

        if (this.updateHistory == null) {
            this.updateHistory = new ArrayList<>();
        }

        this.updateHistory.add(history);
        this.profileUpdateCount = (this.profileUpdateCount == null) ? 1 : this.profileUpdateCount + 1;
    }
}