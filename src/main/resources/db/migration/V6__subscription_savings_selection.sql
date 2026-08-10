-- 절약하기 화면에서 사용자가 "해지 후보"로 체크한 구독을 표시한다.
-- 나중에 알림으로 다시 밀어주기 위해 선택을 서버에 남겨야 한다(화면 로컬 상태로는 알림 배치가 알 수 없다).
--
-- boolean 이 아니라 시각인 이유: 선택 시점을 알아야 "고른 지 N일 지났는데 아직 그대로예요" 같은
-- 알림 문구·주기를 만들 수 있다. 컬럼 수는 같으면서 정보가 더 많다. NULL = 선택 안 함.
ALTER TABLE subscriptions
    ADD COLUMN savings_selected_at DATETIME NULL AFTER deleted_at;
