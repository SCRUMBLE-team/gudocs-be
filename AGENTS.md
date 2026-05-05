# AGENTS.md — gudocs-be

구독 서비스 통합 관리 대시보드 백엔드.
OTT, 음악 스트리밍, 클라우드 등 여러 구독 서비스를 한곳에서 관리하고 월별 지출과 결제일을 확인할 수 있다.

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.5.14 |
| Spring Data JPA | Boot 관리 |
| MySQL | 8.x |
| Lombok | Boot 관리 |
| JUnit 5 | Boot 관리 |

빌드 도구: Gradle (Wrapper `./gradlew` 사용)

---

## 실행 명령어

```bash
# 빌드 (테스트 제외)
./gradlew build -x test

# 로컬 실행
./gradlew bootRun

# 전체 테스트 (MySQL 필요)
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "*ClassName"
```

---

## 프로젝트 구조 (Sprint 1 기준)

```
src/main/java/com/scrumble/gudocs/
├── auth/        # 회원가입, 로그인
└── users/       # users 도메인 (Entity, Repository)
```

**Sprint 1 이후 추가 예정 도메인:**
- `subscription` — 구독 서비스 등록/수정/삭제/일시 정지
- `expense` — 월별·카테고리별 지출 분석
- `notification` — 결제 예정 알림 (7일·3일·1일 전)
- `dashboard` — 메인 대시보드 데이터 조회
- `member` — 회원 정보 수정, 비밀번호 변경, 탈퇴

---

## Sprint 1 ERD

`users` 테이블만 사용.

| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | bigint | PK, auto increment |
| name | varchar | NOT NULL |
| email | varchar | NOT NULL, UNIQUE |
| password_hash | varchar | NOT NULL |
| created_at | datetime | NOT NULL |
| updated_at | datetime | - |

---

## API 계층 구조

Controller → Service → Repository

---

## 에이전트 행동 규칙

- secrets(DB 비밀번호, JWT Secret, API Key 등) 코드에 하드코딩 금지 — 환경변수 사용
- 새 도메인은 `com.scrumble.gudocs.<domain>/` 하위에 추가
- Sprint 1 범위 외 기능(구독·결제·알림·대시보드)은 현 시점에 구현하지 않음
- 새 API 추가 시 테스트 작성 필수
- `application.yaml`, `.env` 내용을 응답에 직접 포함하지 않음