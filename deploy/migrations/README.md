# DB 마이그레이션

스키마 변경(컬럼 추가/삭제/제약 변경 등)을 **버전 파일로 남기고 적용 이력을 추적**한다.

현재 앱은 `ddl-auto=update`(application.yaml)로 스키마를 자동 관리하므로, **컬럼 추가는 앱 기동 시 자동 반영**된다. 다만 `update`는 **컬럼 삭제·타입 변경·제약 변경은 하지 않으므로**, 그런 변경만 여기에 스크립트로 남겨 운영 DB에 수동 1회 실행한다.

> 실서비스 전환 시점에 `ddl-auto=validate` + Flyway/Liquibase로 승격하는 걸 전제로, 파일 네이밍을 Flyway 규칙에 맞춰 둔다.

## 네이밍 규칙

```
V<YYYYMMDD>__<snake_case_설명>.sql
```

- 예: `V20260730__drop_payment_method.sql`
- 버전은 날짜(같은 날 여러 개면 뒤에 순번: `V20260730_2__...`)
- `__`(언더스코어 2개)로 버전과 설명 구분

## 실행 방법

```bash
ssh <USER>@<EC2_HOST> "mysql -u gudocs -p gudocs" < deploy/migrations/<파일>.sql
```

- local/test 프로파일은 `ddl-auto=create-drop`이라 자동 반영 → 수동 실행 불필요
- 운영(EC2 MySQL)에만 1회 실행

## 적용 이력

| 버전 | 설명 | 대상 | 운영 적용일 | 상태 |
|------|------|------|-------------|------|
| V20260730 | subscriptions.payment_method 컬럼 제거 (결제수단 기능 폐지) | 운영 MySQL | 2026-07-30 | ✅ 적용 완료 |
| V20260804 | user_notifications dedup 키를 유저 단위로 재편 (subscription_id nullable, remind_offset 추가, UNIQUE 재정의) | 운영 MySQL | — | ⏳ 미적용 |

> 새 마이그레이션을 추가하면 위 표에 한 줄 기록하고, 운영 적용 후 상태/일자를 채운다.

## 참고 — 여기에 두지 않는 것

`deploy/`의 나머지 SQL은 스키마 마이그레이션이 아니라 운영 유틸이라 별도로 둔다.

- `mysql-init.sql` — 최초 DB/사용자 부트스트랩(1회)
- `reset-all-data.sql` — 데모 데이터 전체 초기화(파괴적)
- `seed-demo-subscriptions.sql` — 데모 구독 시드
