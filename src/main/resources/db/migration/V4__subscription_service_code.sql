-- 구독이 카탈로그 서비스를 가리키는 불변 키(ServiceCatalog.code)를 저장한다.
-- 프론트는 이 값으로 로고를 찾는다. 표시 이름(service_name)은 오타 수정·브랜드 변경으로 바뀔 수 있어
-- 조인 키로 쓸 수 없기 때문이다.
--
-- nullable 인 이유: 카탈로그에 없는 서비스를 사용자가 직접 입력해 등록할 수 있다(그때는 로고도 없다).
-- 기존 행 백필은 하지 않는다 — 승격 시점에 운영 데이터가 없었다.
ALTER TABLE subscriptions
    ADD COLUMN service_code VARCHAR(64) NULL AFTER service_name;
