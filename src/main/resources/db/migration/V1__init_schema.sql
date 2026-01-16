CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alternative_email` varchar(255) DEFAULT NULL,
  `apple_id` varchar(255) DEFAULT NULL,
  `biography` varchar(255) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `google_id` varchar(255) DEFAULT NULL,
  `language_preference` varchar(10) DEFAULT NULL,
  `last_active_date` datetime(6) DEFAULT NULL,
  `last_login_date` datetime(6) DEFAULT NULL,
  `last_updated` datetime(6) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `login_count` int DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(60) DEFAULT NULL,
  `password_reset_token` varchar(64) DEFAULT NULL,
  `password_reset_token_expiry` datetime(6) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `picture` varchar(255) DEFAULT NULL,
  `primary_provider` enum('APPLE','GOOGLE','LOCAL','SOUNDCLOUD','SPOTIFY') NOT NULL,
  `profile_update_count` int DEFAULT NULL,
  `registration_date` datetime(6) DEFAULT NULL,
  `soundcloud_id` varchar(255) DEFAULT NULL,
  `spotify_id` varchar(255) DEFAULT NULL,
  `theme_preference` enum('DARK','LIGHT','SYSTEM') DEFAULT NULL,
  `verification_token` varchar(64) DEFAULT NULL,
  `verification_token_expiry` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_apple_id` (`apple_id`),
  UNIQUE KEY `UK_google_id` (`google_id`),
  UNIQUE KEY `UK_soundcloud_id` (`soundcloud_id`),
  UNIQUE KEY `UK_spotify_id` (`spotify_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_roles` (
  `user_id` bigint NOT NULL,
  `roles` enum('ADMIN','MODERATOR','PREMIUM','USER') NOT NULL,
  KEY `FKhfh9dx7w3ubf1co1vdev94g3f` (`user_id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expiry_date` datetime(6) DEFAULT NULL,
  `token` varchar(255) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_refresh_token` (`token`),
  UNIQUE KEY `UK_refresh_token_user` (`user_id`),
  CONSTRAINT `FK_refresh_token_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `profile_update_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `field_name` varchar(255) NOT NULL,
  `new_value` varchar(1000) DEFAULT NULL,
  `old_value` varchar(1000) DEFAULT NULL,
  `update_date` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_profile_history_user` (`user_id`),
  CONSTRAINT `FK_profile_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_notification_preferences` (
  `user_id` bigint NOT NULL,
  `enabled_notifications` enum('EMAIL_GENERAL','EMAIL_MARKETING','EMAIL_SECURITY','IN_APP_ACTIVITY','IN_APP_GENERAL','IN_APP_MESSAGES','PUSH_ACTIVITY','PUSH_GENERAL','PUSH_MESSAGES') DEFAULT NULL,
  KEY `FK_notification_prefs_user` (`user_id`),
  CONSTRAINT `FK_notification_prefs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `audit_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) NOT NULL,
  `details` varchar(2000) DEFAULT NULL,
  `ip_address` varchar(255) DEFAULT NULL,
  `outcome` varchar(255) DEFAULT NULL,
  `principal` varchar(255) NOT NULL,
  `source` varchar(255) DEFAULT NULL,
  `timestamp` datetime(6) DEFAULT NULL,
  `type` enum('LOGIN_FAILURE','LOGIN_SUCCESS','LOGOUT','PROFILE_UPDATE','ROLE_CHANGE','SECURITY_ALERT','USER_CREATED','USER_DELETED') NOT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
