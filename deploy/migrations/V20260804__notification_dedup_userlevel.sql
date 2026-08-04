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
-- 배포 순서 (중요): 이 스크립트를 **새 코드 배포 전에** 실행한다.
--   ddl-auto=update 로 새 코드가 먼저 기동하면 JPA가 remind_offset 컬럼을 자동 추가한다.
--   그 뒤 아래 3)의 ADD COLUMN 이 "중복 컬럼" 오류를 낸다.
--   → 각 단계를 개별 ALTER 로 분리해, 일부가 이미 반영된 상태에서도 나머지를 이어서 적용할 수 있게 한다.
--     (원자적 단일 ALTER 였다면 한 문장 실패 시 DROP INDEX·UNIQUE 재정의까지 함께 롤백되어
--      dedup 키가 구 버전으로 남는다.) 이미 적용된 단계는 오류가 나면 건너뛰고 다음 단계를 실행한다.
--
-- 사전 정리 (필수): 기존 유니크는 subscription_id 를 포함했으므로 (user_id, type, target_date) 가 같고
--   subscription_id 만 다른 행이 여러 개 존재할 수 있다. 이 경우 4)의 새 유니크 추가가 실패한다.
--   반드시 아래로 중복을 먼저 확인하고, 있으면 각 그룹에서 보존할 1행만 남기고 정리한 뒤 진행한다.
--   -- 확인:
--   SELECT user_id, type, target_date, COUNT(*) c FROM user_notifications
--     GROUP BY user_id, type, target_date HAVING c > 1;
--   -- 정리(예: 그룹별 최소 id 1건만 보존):
--   DELETE t FROM user_notifications t
--     JOIN (SELECT user_id, type, target_date, MIN(id) keep_id FROM user_notifications
--             GROUP BY user_id, type, target_date HAVING COUNT(*) > 1) d
--       ON t.user_id=d.user_id AND t.type=d.type AND t.target_date=d.target_date
--     WHERE t.id <> d.keep_id;
--   (FCM 미가동 데모 DB라면 결제 알림 이력이 없어 중복도 없다)
--
-- 사용법: EC2 MySQL 에서 1회 실행 (각 문장을 순서대로, 실패 시 원인 확인 후 다음 진행).
--   mysql -u gudocs -p gudocs < deploy/migrations/V20260804__notification_dedup_userlevel.sql
-- (local/test 는 create-drop 이라 자동 반영되어 불필요)

USE gudocs;

-- 1) 기존 유니크 제거
ALTER TABLE user_notifications DROP INDEX uk_user_notifications_dedup;

-- 2) subscription_id NULL 허용 (묶음/유저 단위 알림)
ALTER TABLE user_notifications MODIFY COLUMN subscription_id BIGINT NULL;

-- 3) remind_offset 추가 (ddl-auto=update 가 이미 추가했다면 이 문장만 "중복 컬럼" 오류 → 건너뛴다)
ALTER TABLE user_notifications ADD COLUMN remind_offset INT NOT NULL DEFAULT 0 AFTER type;

-- 4) 새 유니크 추가 (사전 정리로 중복이 없어야 성공)
ALTER TABLE user_notifications
    ADD CONSTRAINT uk_user_notifications_dedup
        UNIQUE (user_id, type, target_date, remind_offset);
