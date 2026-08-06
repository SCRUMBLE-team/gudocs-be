-- user_notifications 를 엔티티 최종형으로 정합화 (기존 deploy/migrations/V20260804 승계 + enum 드리프트 수정)
--
-- 운영 DB(baseline V1)는 ddl-auto=update 로 인해 remind_offset 컬럼만 자동 추가된 하이브리드 상태다.
-- 아래로 dedup 키·nullable·enum 을 엔티티 기준으로 수렴시킨다.
-- (신규 빈 DB 는 V1 이 하이브리드 스키마를 만든 뒤 이 V2 가 동일하게 정합화 → 최종 상태 일치)
--
-- 변경:
--   - subscription_id: NOT NULL -> NULL 허용 (묶음/유저 단위 알림)
--   - type enum: SUBSCRIPTION_REVIEW 값 추가 (없으면 검사 유도 알림 INSERT 실패)
--   - UNIQUE(user_id, subscription_id, type, target_date)
--       -> UNIQUE(user_id, type, target_date, remind_offset)
--   (remind_offset 컬럼은 baseline 에 이미 존재 → 추가하지 않음)

-- 1) 새 유니크 키(user_id, type, target_date, remind_offset) 기준 중복 사전 정리
--    (구 유니크는 subscription_id 를 포함했으므로 새 키로는 충돌하는 행이 있을 수 있음)
DELETE t FROM user_notifications t
  JOIN (
    SELECT user_id, type, target_date, remind_offset, MIN(id) AS keep_id
      FROM user_notifications
     GROUP BY user_id, type, target_date, remind_offset
    HAVING COUNT(*) > 1
  ) d
    ON t.user_id = d.user_id
   AND t.type = d.type
   AND t.target_date = d.target_date
   AND t.remind_offset = d.remind_offset
 WHERE t.id <> d.keep_id;

-- 2) 구 유니크 제거
ALTER TABLE user_notifications DROP INDEX uk_user_notifications_dedup;

-- 3) subscription_id NULL 허용
ALTER TABLE user_notifications MODIFY COLUMN subscription_id BIGINT NULL;

-- 4) type enum 에 SUBSCRIPTION_REVIEW 추가 (엔티티 선언 순서와 일치)
ALTER TABLE user_notifications
  MODIFY COLUMN type ENUM('BILLING_REMINDER','SUBSCRIPTION_REVIEW') NOT NULL;

-- 5) 새 유니크 추가
ALTER TABLE user_notifications
  ADD CONSTRAINT uk_user_notifications_dedup
      UNIQUE (user_id, type, target_date, remind_offset);
