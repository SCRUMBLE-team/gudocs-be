-- 데모용 구독 시드: 특정 소셜 계정(FE 개발자) 앞으로 테스트 구독을 넣는 1회성 스크립트.
--
-- 전제:
--   1) 데이터 초기화(테이블 드롭) 후 새 앱이 떠서 스키마가 재생성돼 있어야 한다.
--   2) 대상 FE 개발자가 소셜 로그인을 **최소 1회** 해서 users / social_accounts 행이 생성돼 있어야 한다.
--      (식별은 provider + provider_id. DataInitializer 의 mock 계정은 local/H2 전용이라 운영에는 없다.)
--
-- 사용법: 아래 @provider / @provider_id 를 실제 값으로 채우고 EC2 MySQL 에서 1회 실행.
--   - provider    : 'GOOGLE' | 'KAKAO' | 'NAVER'
--   - provider_id : 소셜 제공자가 준 고유 식별자(예: Google 의 sub 값). social_accounts.provider_id 와 동일.
--     확인:  SELECT user_id, provider, provider_id, email FROM social_accounts;

USE gudocs;

SET @provider    := 'GOOGLE';
SET @provider_id := 'PASTE_PROVIDER_ID_HERE';

SET @uid := (SELECT user_id FROM social_accounts
             WHERE provider = @provider AND provider_id = @provider_id);

-- @uid 가 NULL 이면 대상 계정이 아직 로그인하지 않은 것 → 로그인 후 다시 실행할 것.
-- (아래 INSERT 는 user_id NOT NULL 이라 그대로 실패하며 안내 역할을 한다.)

INSERT INTO subscriptions
  (user_id, service_name, category, price, billing_cycle, first_billing_date,
   status, paused_at, deleted_at, created_at, updated_at)
VALUES
  (@uid, 'Netflix',               'OTT',       17000, 'MONTHLY', '2025-01-05', 'ACTIVE', NULL,   NULL, NOW(), NOW()),
  (@uid, 'YouTube Premium',       'OTT',       14900, 'MONTHLY', '2025-01-10', 'ACTIVE', NULL,   NULL, NOW(), NOW()),
  (@uid, 'Spotify',               'MUSIC',     10900, 'MONTHLY', '2025-01-15', 'ACTIVE', NULL,   NULL, NOW(), NOW()),
  (@uid, 'iCloud+',               'CLOUD',      3900, 'MONTHLY', '2025-01-01', 'ACTIVE', NULL,   NULL, NOW(), NOW()),
  (@uid, 'Google One',            'CLOUD',      2900, 'MONTHLY', '2025-01-08', 'ACTIVE', NULL,   NULL, NOW(), NOW()),
  (@uid, 'ChatGPT Plus',          'AI',        24000, 'MONTHLY', '2025-01-20', 'ACTIVE', NULL,   NULL, NOW(), NOW()),
  (@uid, 'Adobe Creative Cloud',  'DESIGN',   624000, 'YEARLY',  '2025-03-01', 'PAUSED', NOW(),  NULL, NOW(), NOW()),
  (@uid, '인프런',                 'EDUCATION', 29000, 'MONTHLY', '2025-01-25', 'ACTIVE', NULL,   NULL, NOW(), NOW());
