-- payment_method 컬럼 제거 마이그레이션 (결제수단 기능 폐지)
--
-- 배경: subscriptions.payment_method 를 엔티티/DTO/응답에서 완전히 제거했다.
-- 운영 DB(application.yaml ddl-auto=update)는 컬럼을 자동으로 DROP 하지 않으므로,
-- 남아있는 NOT NULL 컬럼 때문에 신규 INSERT(구독 등록)가 실패한다. 1회 수동 실행 필요.
--
-- 사용법: EC2 MySQL 에서 1회 실행.
--   mysql -u gudocs -p gudocs < deploy/migrations/V20260730__drop_payment_method.sql
-- (local/test 는 create-drop 이라 자동 반영되어 불필요)

USE gudocs;

ALTER TABLE subscriptions DROP COLUMN payment_method;
