-- 공식 가격 변경 알림(PRICE_CHANGE) 도입.
--
-- 스키마 변경은 이 enum 값 하나가 전부다. 가격 변경 "이벤트"는 DB가 아니라 ServiceCatalog.java 에
-- 선언한다 — 공식 출처 검증을 사람이 하기로 한 이상, 검증 절차는 그 파일을 고치는 PR 리뷰이고
-- DETECTED/VERIFIED 상태를 DB에 또 두면 같은 검증을 두 벌 운영하게 된다.
--
-- 중복 발송 방지도 기존 dedup 키를 그대로 쓴다: target_date = 적용 예정일이므로
-- UNIQUE(user_id, type, target_date, remind_offset, subscription_id) 가 곧
-- "사용자 × 구독 × 그 인상 건 1회"가 된다. 매일 배치가 돌아도 구독당 한 번만 나간다.
ALTER TABLE user_notifications
  MODIFY COLUMN type ENUM('BILLING_REMINDER','SUBSCRIPTION_REVIEW','CANCEL_REMINDER','PRICE_CHANGE') NOT NULL;
