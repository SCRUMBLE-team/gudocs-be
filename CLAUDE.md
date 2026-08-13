# CLAUDE.md — gudocs-be

@AGENTS.md

구독 서비스 통합 관리 대시보드 백엔드. Spring Boot 3.5 / Java 21 / MySQL 8.

아키텍처·ERD·API 목록·배포는 위 `AGENTS.md`가 단일 소스다. 이 문서는 Claude 전용 추가 규칙만 담는다.

---

## 핵심 명령어

```bash
./gradlew build -x test                                      # 빌드
./gradlew bootRun                                            # 로컬 (MySQL)
./gradlew bootRun --args='--spring.profiles.active=local'    # H2 + mock data
./gradlew test                                               # 전체 테스트
./gradlew test --tests "*ClassName"                          # 단일 클래스
```

---

## 리소스 파일

| 파일 | 프로파일 | 설명 |
|------|---------|------|
| `src/main/resources/application.yaml` | 기본 | MySQL, Flyway 적용 (`ddl-auto=validate`) |
| `src/main/resources/application-local.yaml` | local | H2 인메모리, H2 콘솔(`/h2-console`), Flyway 비활성 |
| `src/test/resources/application.yaml` | test | H2 인메모리, create-drop, Spring Session 자동설정 제외 |
| `src/main/resources/db/migration/` | — | Flyway 마이그레이션 (기동 시 자동 적용) |

**local 프로파일 mock 데이터** — `config/DataInitializer`가 mock 사용자(`test@test.com`) + 소셜 계정 + 구독 8개를 삽입한다.

---

## 작업 규칙

* 새 도메인은 `com.scrumble.gudocs.<domain>/` 하위에 Controller / Service / Repository 생성
* 마이페이지 기능은 새 도메인을 만들지 않고 기존 `users`에 추가한다
* 새 API 추가 시 테스트 클래스도 함께 작성
* 수정성 API는 `PATCH`가 아니라 `PUT` (full update — 모든 필드 필수)
* Controller는 `@CurrentUserId Long userId`로 현재 사용자 id를 주입받고, 그 id 기준으로만 조회/수정/삭제한다
* DTO는 Java `record` 우선. Request DTO에는 Bean Validation 적용
* 응답은 `ApiResponse`, 예외는 `BusinessException` + `ErrorCode` + `GlobalExceptionHandler` 구조를 따른다
* 같은 의미의 `ErrorCode`가 이미 있으면 새로 만들지 말고 재사용한다
* 스키마 변경(컬럼 추가는 물론 **nullability 변경 포함**)은 Flyway 마이그레이션을 함께 작성한다 — `validate`는 nullability를 검사하지 않고 H2 테스트도 잡지 못해, 운영 INSERT 시점 500으로만 드러난다
* 기능을 추가·변경하면 같은 작업에서 `AGENTS.md`(아키텍처·ERD·API 표)를 함께 갱신한다
* 문서 작성 규칙은 `AI_DOCS_GUIDE.md` — CLAUDE.md 300줄 초과 금지, AGENTS.md와 중복 금지

---

## 브랜치 & 커밋

* 브랜치: `feat/<이슈번호>-<설명>`, `fix/<이슈번호>-<설명>`
* 커밋 메시지: 한국어, `feat: 회원가입 API 구현` 형식
* PR base는 `develop` (main 직접 금지), 본문에 관련 이슈 번호 연결

---

## 절대 하지 말 것

* secrets(DB 비밀번호, OAuth Client Secret, Firebase 키 등) 코드에 하드코딩하거나 응답에 노출
* `application.yaml` / `.env` 내용을 응답에 포함
* 기존 인증 구조(OAuth2 소셜 로그인 전용), SecurityConfig, 세션 정책을 임의로 변경
* 다른 사용자의 데이터에 접근 가능한 API 구현
* hard delete — 구독 삭제는 `deleted_at` soft delete
* 회원 탈퇴 시 구독·소셜계정·청구기록·푸시 데이터가 남도록 구현
* 과거 지출을 `subscriptions` 테이블로 계산 — 금액은 `billing_records`에서만 읽는다
* `billing_records`를 실제 카드 결제 증빙으로 취급 (등록정보 기반 추정치다)
* 정확성·데이터 무결성 결함을 "규모에 비해 과하다"는 이유로 문서화만 하고 넘기기 — 계속 운영하는 서비스다
