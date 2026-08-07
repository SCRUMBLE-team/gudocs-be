# DB 마이그레이션

> **⚠️ Flyway로 승격됨 (2026-08-07).** 스키마 마이그레이션은 이제 **`src/main/resources/db/migration/`** 의 Flyway 버전 파일로 관리하고, **앱 기동 시 자동 적용**된다(운영 배포 = `systemctl restart` 만으로 반영). `application.yaml` 은 `ddl-auto=validate`(Flyway가 스키마 단일 소스).
>
> **이 디렉터리(`deploy/migrations/`)의 `V202608*.sql` 은 승격 이전의 수동 실행용 아카이브**다. 신규 마이그레이션을 여기 추가하지 말 것 → `src/main/resources/db/migration/` 에 작성한다.

## Flyway 운영 방식 (현재)

- 파일 위치: `src/main/resources/db/migration/V<n>__<snake_case>.sql`
- `V1__baseline.sql` = 승격 시점 운영 DB 스키마 스냅샷. 기존 운영 DB는 `flyway.baseline-on-migrate=true` + `baseline-version=1` 로 **V1을 "이미 적용됨"으로 봉인**하고 V2부터 적용한다. 빈 DB는 V1부터 전부 실행.
- local/test 프로파일은 `ddl-auto=create-drop` + `flyway.enabled=false`(H2, MySQL 방언 미적용) → Flyway 무관.
- 세션 테이블(SPRING_SESSION)도 `V3__spring_session.sql` 로 생성된다.

### 아카이브 (수동 실행 시절, 참고용)

| 버전 | 설명 | 상태 |
|------|------|------|
| V20260730 | subscriptions.payment_method 컬럼 제거 | ✅ 운영 적용 완료 → V1 baseline에 흡수 |
| V20260804 | user_notifications dedup 유저 단위 재편 | ⏳ 수동 미적용 → Flyway `V2` 로 승계(배포 시 자동 적용) |

## 참고 — 여기에 두지 않는 것

`deploy/`의 나머지 SQL은 스키마 마이그레이션이 아니라 운영 유틸이라 별도로 둔다.

- `mysql-init.sql` — 최초 DB/사용자 부트스트랩(1회)
- `reset-all-data.sql` — 데모 데이터 전체 초기화(파괴적)
- `seed-demo-subscriptions.sql` — 데모 구독 시드
