-- user_notifications dedup 키를 유저 단위 + 발송 단계(remind_offset) 기준으로 재편
--
-- 배경:
--   1) 결제 알림을 "같은 결제일 구독 묶음 1건"으로 발송하도록 바꾼다 → 알림 1건이 여러 구독을
--      대표하므로 subscription_id 를 특정할 수 없다(NULL 허용 필요, dedup 키에서 제외).
--   2) 검사 유도(SUBSCRIPTION_REVIEW) 알림은 구독이 아니라 "유저" 단위라 subscription_id 가 없다.
--   3) 결제 알림은 같은 결제일(target_date)에 대해 D-3 과 당일(D-0) 두 번 발송된다.
--      target_date 만으로는 이 둘을 구분 못 해 서로 중복 처리되므로, 발송 단계 discriminator
--      remind_offset(결제 며칠 전: 3 또는 0)을 dedup 키에 넣는다.
--
-- 변경:
--   - subscription_id: NOT NULL -> NULL 허용
--   - remind_offset INT NOT NULL DEFAULT 0 컬럼 추가 (검사 유도는 0)
--   - UNIQUE(user_id, subscription_id, type, target_date)
--       -> UNIQUE(user_id, type, target_date, remind_offset)
--
-- 주의: 기존 유니크는 subscription_id 를 포함했으므로 (user_id, type, target_date) 가 같고
--   subscription_id 만 다른 행이 여러 개 존재할 수 있다. 이 경우 새 유니크 추가가 실패하므로,
--   먼저 아래로 중복을 확인하고(있으면 오래된 행 정리 후) 마이그레이션을 실행한다.
--   SELECT user_id, type, target_date, COUNT(*) c FROM user_notifications
--     GROUP BY user_id, type, target_date HAVING c > 1;
--   (FCM 미가동 데모 DB라면 결제 알림 이력이 없어 중복도 없다)
--
-- 사용법: EC2 MySQL 에서 1회 실행.
--   mysql -u gudocs -p gudocs < deploy/migrations/V20260804__notification_dedup_userlevel.sql
-- (local/test 는 create-drop 이라 자동 반영되어 불필요)

USE gudocs;

ALTER TABLE user_notifications
    DROP INDEX uk_user_notifications_dedup,
    MODIFY COLUMN subscription_id BIGINT NULL,
    ADD COLUMN remind_offset INT NOT NULL DEFAULT 0 AFTER type,
    ADD CONSTRAINT uk_user_notifications_dedup
        UNIQUE (user_id, type, target_date, remind_offset);
