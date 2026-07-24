-- 구독 결제일 모델 전환: billing_day + billing_month → first_billing_date(앵커) 통합용 1회성 스크립트.
--
-- 배경: Subscription 엔티티가 billing_day / billing_month 두 컬럼 대신
--       first_billing_date(NOT NULL) 하나로 바뀌었다. 다음 결제일은 저장하지 않고
--       NextBillingDateCalculator 가 (앵커의 day-of-month + month) 로 재계산한다.
--       그런데 ddl-auto: update 는
--         1) 이미 존재하는 billing_day(NOT NULL) / billing_month 컬럼을 자동으로 드롭하지 않는다.
--            → 구 스키마로 한 번이라도 뜬 운영 DB에서는 새 코드가 billing_day 를 채우지 않아
--              신규 구독 INSERT 가 "Field 'billing_day' doesn't have a default value" 로 실패한다.
--         2) first_billing_date 를 곧바로 NOT NULL 로 추가하려다 기존 행이 있으면 실패할 수 있다.
--       그래서 nullable 추가 → 백필 → NOT NULL → 구 컬럼 제거 순서로 직접 처리한다.
--
-- 실행 시점: **새 애플리케이션을 배포/재시작하기 전**에 EC2 MySQL 에서 1회 실행한다.
--            (구 스키마 상태에서 실행해야 billing_day/billing_month 로 백필할 수 있다.)
--            실행 후 새 앱이 뜨면 Hibernate update 는 이미 일치하는 스키마를 보고 아무것도 하지 않는다.
--
-- 신규로 새 DB에 배포한 경우(구 스키마 이력 없음)에는 실행 불필요.

USE gudocs;

-- 1) nullable 로 컬럼 추가
ALTER TABLE subscriptions
  ADD COLUMN first_billing_date DATE NULL AFTER billing_cycle;

-- 2) 기존 billing_day / billing_month 로 백필
--    - 앵커는 "day-of-month(+ YEARLY 는 month)" 만 의미가 있으므로 연도는 고정값(2025)으로 둔다.
--    - MONTHLY: month=1 (1월은 31일까지라 billing_day 1~31 모두 유효)
--    - YEARLY : month=billing_month, day 는 해당 월 말일로 클램프(예: 2월 31일 → 2월 말일)
UPDATE subscriptions
SET first_billing_date = STR_TO_DATE(
      CONCAT('2025-01-', LPAD(billing_day, 2, '0')), '%Y-%m-%d')
WHERE billing_cycle = 'MONTHLY';

UPDATE subscriptions
SET first_billing_date = STR_TO_DATE(
      CONCAT(
        '2025-', LPAD(billing_month, 2, '0'), '-',
        LPAD(
          LEAST(
            billing_day,
            DAYOFMONTH(LAST_DAY(STR_TO_DATE(CONCAT('2025-', LPAD(billing_month, 2, '0'), '-01'), '%Y-%m-%d')))
          ),
          2, '0')
      ), '%Y-%m-%d')
WHERE billing_cycle = 'YEARLY';

-- 3) NOT NULL 제약 적용
ALTER TABLE subscriptions
  MODIFY COLUMN first_billing_date DATE NOT NULL;

-- 4) 구 컬럼 제거
ALTER TABLE subscriptions
  DROP COLUMN billing_day,
  DROP COLUMN billing_month;
