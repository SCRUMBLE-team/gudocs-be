-- 구독 결제일 모델 전환: billing_day + billing_month → first_billing_date(앵커) 통합.
--
-- 배경: Subscription 엔티티가 billing_day / billing_month 두 컬럼 대신
--       first_billing_date(NOT NULL) 하나로 바뀌었다.
--       그런데 ddl-auto: update 는 구 billing_day(NOT NULL) 컬럼을 자동으로 드롭하지 않아,
--       구 스키마로 뜬 운영 DB에서는 새 코드가 billing_day 를 채우지 않아
--       신규 구독 INSERT 가 "Field 'billing_day' doesn't have a default value" 로 실패한다.
--
-- 정책: 기존 구독 데이터는 보존하지 않는다(데모용, 버려도 됨).
--       → 백필 없이 subscriptions 테이블을 통째로 드롭하고,
--         새 앱이 뜰 때 Hibernate(update)가 first_billing_date 포함한 새 스키마로 재생성한다.
--
-- 실행법 (EC2 MySQL 에서 1회):
--   USE gudocs;
--   DROP TABLE IF EXISTS subscriptions;
--   → 이후 새 애플리케이션을 배포/재시작하면 테이블이 새 스키마로 다시 생성된다.
--   → 데모 데이터는 로그인 후 앱(UI/API)에서 다시 등록한다.
--     (mock 데이터 자동 삽입 DataInitializer 는 local 프로파일=H2 전용이라 운영에는 적용되지 않는다.)
--
-- 신규로 새 DB에 배포한 경우(구 스키마 이력 없음)에는 실행 불필요.

USE gudocs;

DROP TABLE IF EXISTS subscriptions;
