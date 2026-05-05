# CLAUDE.md — gudocs-be

@AGENTS.md

구독 서비스 통합 관리 대시보드 백엔드. Spring Boot 3.5 / Java 21 / MySQL.

---

## 핵심 명령어

```bash
# 빌드 (테스트 제외)
./gradlew build -x test

# 로컬 실행
./gradlew bootRun

# 전체 테스트
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "*ClassName"
```

---

## 아키텍처

패키지 루트: `com.scrumble.gudocs`

**Sprint 1 도메인 (현재 구현 범위):**

| 도메인 | 경로 | 기능 |
|--------|------|------|
| auth | `auth/` | 회원가입 (`POST /auth/signup`), 로그인 (`POST /auth/login`) |
| users | `users/` | users 테이블 Entity / Repository |

계층 구조: Controller → Service → Repository

---

## 작업 규칙

- 새 도메인 추가 시 `com.scrumble.gudocs.<domain>/` 하위에 Controller, Service, Repository 생성
- Sprint 1 범위는 `auth`, `users`만. 구독·결제·알림·대시보드 코드는 작성하지 않음
- 새 API 추가 시 테스트 클래스도 함께 작성

## 브랜치 & 커밋

- 브랜치: `feat/<이슈번호>-<설명>`, `fix/<이슈번호>-<설명>`
- 커밋 메시지: 한국어, `feat: 회원가입 API 구현` 형식
- PR 본문에 관련 이슈 번호 연결

---

## 절대 하지 말 것

- secrets(DB 비밀번호, JWT Secret 등) 코드 또는 응답에 노출
- `application.yaml` 내용을 응답에 직접 포함
- Sprint 1 범위 밖 기능 선제 구현