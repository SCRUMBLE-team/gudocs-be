-- Flyway baseline (V1) — 운영 DB 실제 스키마 스냅샷 (mysqldump --no-data 기준, 2026-08-07)
--
-- 이 파일은 "현재 운영 DB 상태"를 그대로 재현한다. 운영 DB는 이미 이 스키마로 존재하므로
-- application.yaml 의 flyway.baseline-version=1 + baseline-on-migrate=true 에 의해
-- 운영에서는 실행되지 않고 "이미 적용됨"으로 봉인된다. (신규/빈 DB 에서만 실제로 실행됨)
--
-- 주의: 운영 DB 는 ddl-auto=update 로 관리돼 온 하이브리드 상태다.
--   user_notifications 는 아직 구 dedup 키 + subscription_id NOT NULL + type enum 에
--   SUBSCRIPTION_REVIEW 누락 상태다. 이 드리프트는 V2 에서 정합화한다(엔티티 기준으로 수렴).

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `social_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `email_verified` bit(1) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `provider` enum('GOOGLE','KAKAO','NAVER') NOT NULL,
  `provider_id` varchar(255) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_social_provider_provider_id` (`provider`,`provider_id`),
  UNIQUE KEY `uk_social_user_provider` (`user_id`,`provider`),
  CONSTRAINT `fk_social_accounts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `billing_cycle` enum('MONTHLY','YEARLY') NOT NULL,
  `category` enum('AI','CLOUD','DESIGN','EDUCATION','ETC','GAME','MUSIC','NEWS','OTT','PRODUCTIVITY','SHOPPING') NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `first_billing_date` date NOT NULL,
  `paused_at` datetime(6) DEFAULT NULL,
  `price` bigint NOT NULL,
  `service_name` varchar(255) NOT NULL,
  `status` enum('ACTIVE','PAUSED') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_subscriptions_user` (`user_id`),
  CONSTRAINT `fk_subscriptions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `push_registrations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `device_name` varchar(255) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `fid` varchar(255) NOT NULL,
  `last_registered_at` datetime(6) NOT NULL,
  `platform` enum('WEB') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_registrations_fid` (`fid`),
  KEY `idx_push_registrations_user` (`user_id`),
  CONSTRAINT `fk_push_registrations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 하이브리드 상태 그대로: subscription_id NOT NULL, type enum 에 SUBSCRIPTION_REVIEW 없음,
-- 구 dedup 유니크. (V2 에서 정합화)
CREATE TABLE `user_notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `body` varchar(500) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `subscription_id` bigint NOT NULL,
  `target_date` date NOT NULL,
  `title` varchar(255) NOT NULL,
  `type` enum('BILLING_REMINDER') NOT NULL,
  `user_id` bigint NOT NULL,
  `remind_offset` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_notifications_dedup` (`user_id`,`subscription_id`,`type`,`target_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
