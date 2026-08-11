-- 지출 분석을 "구독에서 매번 계산" → "등록정보 기반 청구 스냅샷 조회"로 전환.
--
-- 문제: 과거 지출을 subscriptions 에서 계산하면 사용자가 나중에 가격·카테고리를 바꾸거나 정지·삭제할 때
-- 과거가 따라 움직인다. 특히 paused_at 은 "마지막으로 정지한 시각" 하나뿐이라 재개하면 지워져,
-- 3월 정지 → 8월 재개한 구독의 4~7월이 결제한 것으로 집계됐다(내지 않은 돈).
--
-- 필드마다 변경 이력 테이블을 두는 방법도 있지만(가격 이력·카테고리 이력·상태 이력 …) 가변 필드가
-- 늘 때마다 이력이 늘고 조회는 시점 조인이 된다. 결제 시점의 값을 통째로 얼려 한 줄 남기면 그게 전부
-- 필요 없어진다. 정지는 "그 달에 행이 없다"로 저절로 표현된다.
CREATE TABLE billing_records (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    subscription_id BIGINT       NOT NULL,
    billing_date    DATE         NOT NULL,
    -- 기록 생성 시점의 subscriptions.price. 카드·은행에서 확인한 실제 승인 금액이 아니다.
    amount          BIGINT       NOT NULL,
    -- 결제 시점의 구독 정보 스냅샷. 지금 구독이 어떤 상태든 과거 표시는 이 값으로 한다.
    service_name    VARCHAR(255) NOT NULL,
    service_code    VARCHAR(64)  NULL,
    category        ENUM('OTT','MUSIC','CLOUD','PRODUCTIVITY','AI','NEWS','EDUCATION','GAME','SHOPPING','DESIGN','ETC') NOT NULL,
    billing_cycle   ENUM('MONTHLY','YEARLY') NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- 배치가 지난 며칠치를 다시 훑어도 중복 행이 생기지 않게 하는 멱등 키.
    UNIQUE KEY uk_billing_records_subscription_date (subscription_id, billing_date),
    -- 지출 조회는 사용자 + 결제일 구간으로만 끝난다(구독 조인 없음).
    KEY idx_billing_records_user_date (user_id, billing_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기존 구독의 과거 결제는 백필하지 않는다. 승격 시점에 보존할 만한 운영 데이터가 없었고, 과거 상태·가격
-- 변경 이력이 남아 있지 않아 현재 값으로 채워 봐야 추정치이기 때문. 이 테이블은 배치가 도는 날부터
-- 일관되게 기록된다. 단, 카드·은행의 실제 결제 증빙은 아니며 실제 승인 여부·금액을 판정할 수 없다.
-- 신규 등록 구독의 과거 백필도 현재 입력값으로 과거 청구 일정을 추정한 값이다.

-- paused_at 은 더 이상 지출 판정에 쓰지 않는다. "언제 정지했나"라는 현재 상태 정보로서는 남겨 두되,
-- 과거 지출을 이 컬럼으로 계산하는 코드를 다시 만들지 말 것 — 위에 적은 이유로 반드시 틀린다.
