# Gudocs — 구독 서비스 통합 관리 대시보드

구독 중인 OTT, 음악, 클라우드, AI 툴 등 다양한 서비스를 한곳에서 관리하고,  
월별 지출을 분석할 수 있는 웹 애플리케이션의 백엔드 서버입니다.

**Frontend Repository:** [gudocs-fe](https://github.com/SCRUMBLE-team/gudocs-fe)  
**API 문서 (Swagger):** [https://43-203-195-12.sslip.io/swagger-ui/index.html](https://43-203-195-12.sslip.io/swagger-ui/index.html)

---

## 프로젝트 정보

| 항목 | 내용 |
|------|------|
| 기간 | 2026.05 ~ 2026.06 (약 5주) |
| 인원 | 5명 (백엔드 2, 프론트엔드 3) |

### 팀원

| 이름                                      | 역할 |
|-----------------------------------------|------|
| [Gopistol](https://github.com/Gopistol) | 백엔드 |
| [2SEONGA](https://github.com/2SEONGA)   | 백엔드 |

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| ORM | Spring Data JPA |
| Database | MySQL 8 (운영) / H2 (로컬·테스트) |
| Auth | Spring Security (세션 기반) |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| Test | JUnit 5, Jacoco |
| Build | Gradle |
| Deploy | AWS EC2 · Caddy · GitHub Actions |

---

## 주요 기능

- **인증** — 회원가입 / 로그인 / 로그아웃 (세션 쿠키 방식)
- **마이페이지** — 이름·비밀번호 수정, 회원 탈퇴 (탈퇴 시 구독 데이터 연쇄 삭제)
- **구독 관리** — 구독 등록·수정·삭제(soft delete)·상태 전환 (ACTIVE / PAUSED)
- **지출 분석** — 월별 지출, 카테고리별 비율, 6개월 추이, 월별 상세 내역
- **대시보드** — 이번 달 총 지출·카테고리 요약·결제 예정 알림 집계

---

## 시스템 아키텍처

```
[브라우저]
    │ HTTPS
    ▼
Vercel (gudocs-fe)
    │ fetch (credentials: include)
    ▼
AWS EC2 t3.micro (Ubuntu 22.04)
  Caddy :443  →  Spring Boot :8080  →  MySQL 8 :3306
```

---

## ERD

```
users
├── id            BIGINT PK
├── name          VARCHAR
├── email         VARCHAR UNIQUE
├── password_hash VARCHAR
├── created_at    DATETIME
└── updated_at    DATETIME

subscriptions
├── id             BIGINT PK
├── user_id        BIGINT FK → users.id
├── service_name   VARCHAR
├── category       ENUM (OTT, MUSIC, CLOUD, PRODUCTIVITY, AI, NEWS, EDUCATION, GAME, SHOPPING, DESIGN, ETC)
├── price          BIGINT
├── billing_cycle  ENUM (MONTHLY, YEARLY)
├── billing_day    INT
├── billing_month  INT          -- YEARLY 전용
├── payment_method ENUM (CARD, BANK_TRANSFER, SIMPLE_PAY, ETC)
├── status         ENUM (ACTIVE, PAUSED)
├── paused_at      DATETIME
├── deleted_at     DATETIME     -- soft delete
├── created_at     DATETIME
└── updated_at     DATETIME
```

---

## CI/CD

- **PR / develop push** → GitHub Actions에서 테스트 + 빌드 자동 실행
- **main push** → 빌드 후 EC2에 JAR 배포 및 서비스 재시작 자동화
