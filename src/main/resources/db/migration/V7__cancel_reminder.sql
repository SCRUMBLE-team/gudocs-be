-- 해지 알림(CANCEL_REMINDER) 도입.
--
-- 절약 후보로 체크한 구독은 D-3 결제 예정 알림 대신 해지 알림을 받는다. 이 알림은 클릭 시 해당 구독
-- 상세로 보내야 하므로 묶음이 아니라 **구독별 1건**으로 발송한다. 그래서 dedup 키에 subscription_id 가
-- 필요하다(같은 날 같은 단계의 서로 다른 구독을 구분해야 함).
--
-- subscription_id 를 NULL 이 아니라 0(= "특정 구독 없음") 으로 두는 이유:
-- MySQL 의 UNIQUE 인덱스는 NULL 을 서로 다른 값으로 취급한다. NULL 을 그대로 두고 키에 넣으면
-- 묶음 알림(결제 예정·검사 유도)은 같은 행이 몇 번이든 들어가 **중복 발송 방지가 풀린다.**
ALTER TABLE user_notifications
  MODIFY COLUMN type ENUM('BILLING_REMINDER','SUBSCRIPTION_REVIEW','CANCEL_REMINDER') NOT NULL;

UPDATE user_notifications SET subscription_id = 0 WHERE subscription_id IS NULL;

ALTER TABLE user_notifications
  MODIFY COLUMN subscription_id BIGINT NOT NULL DEFAULT 0;

ALTER TABLE user_notifications DROP INDEX uk_user_notifications_dedup;

ALTER TABLE user_notifications
  ADD CONSTRAINT uk_user_notifications_dedup
      UNIQUE (user_id, type, target_date, remind_offset, subscription_id);
