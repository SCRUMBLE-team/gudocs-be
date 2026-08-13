-- 정지/재개 구간 이력. "그 달에 이 구독이 정지 중이었나"를 답하기 위한 사건 기록이다.
--
-- 문제: subscriptions.paused_at 은 "마지막으로 정지한 시각" 하나뿐이라 과거를 복원하지 못한다.
--   6월 정지 → 7월 재개  → paused_at 이 null 로 지워져 6월이 왜 비었는지 알 수 없다.
--   6월 정지 → 7월 재개 → 8월 재정지 → paused_at=8월이라 "6월은 정상"이라고 틀리게 답한다.
-- 되돌릴 수 있는 사건(정지↔재개)을 값 컬럼 하나로 덮어쓰면 반드시 과거를 잃는다(AGENTS.md).
--
-- 금액은 여전히 billing_records 에서만 나온다. 이 표는 표시용 라벨(그 달 상태) 전용이며,
-- 이 값으로 지출을 되계산하지 말 것 — 이력과 기록이 어긋나면 있지도 않은 결제를 만들어낸다.
CREATE TABLE subscription_pause_periods (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT      NOT NULL,
    -- 정지가 시작된 시각.
    started_at      DATETIME(6) NOT NULL,
    -- 재개한 시각. NULL 이면 아직 정지 중인 열린 구간이다(구독당 최대 1개).
    ended_at        DATETIME(6) NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_pause_periods_subscription (subscription_id, started_at),
    CONSTRAINT fk_pause_periods_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 승격 시점에 이미 정지 중인 구독은 열린 구간으로 옮긴다. 시작 시각은 paused_at 이 유일한 근거다.
-- (paused_at 이 비어 있는 비정상 행은 updated_at 으로 대신한다 — 없는 것보다 근사값이 낫다.)
--
-- 과거에 정지했다가 재개한 이력은 백필하지 않는다. 그 정보는 애초에 어디에도 남아 있지 않다.
-- 그래서 이 표가 생기기 전의 달은 "그 달 상태"를 ACTIVE 로 답한다 — 모르는 것을 지어내지 않는다.
INSERT INTO subscription_pause_periods (subscription_id, started_at, ended_at, created_at, updated_at)
SELECT id, COALESCE(paused_at, updated_at, created_at), NULL, NOW(6), NULL
FROM subscriptions
WHERE status = 'PAUSED';
