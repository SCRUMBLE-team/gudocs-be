-- 운영 데모 DB 전체 초기화용 1회성 스크립트. 모든 앱 데이터를 삭제한다(복구 불가).
--
-- 용도: 결제일 모델 전환 등으로 스키마를 갈아엎고 처음부터 다시 시작할 때.
--       테이블을 드롭하면 새 앱 기동 시 Hibernate(ddl-auto: update)가 최신 스키마로 재생성한다.
--
-- 이후 흐름:
--   1) (이 스크립트 실행) 테이블 드롭
--   2) 새 애플리케이션 배포/재시작 → 스키마 재생성
--   3) 대상 사용자(FE 개발자 등) 소셜 로그인 1회 → users / social_accounts 생성
--   4) deploy/seed-demo-subscriptions.sql 로 구독 테스트 데이터 삽입

USE gudocs;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS social_accounts;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;
