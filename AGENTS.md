# AGENTS.md — gudocs-be

구독 서비스 통합 관리 대시보드 백엔드. Spring Boot 3.5 / Java 21 / MySQL 8.

---

## 실행

```bash
./gradlew build -x test                                      # 빌드
./gradlew bootRun                                            # 로컬 (MySQL)
./gradlew bootRun --args='--spring.profiles.active=local'    # H2 + mock data
./gradlew test                                               # 전체 테스트
./gradlew test --tests "*ClassName"                          # 단일 클래스
```

---

## 패키지 구조

```
src/main/java/com/scrumble/gudocs/
├── auth/           # 회원가입, 로그인, 로그아웃, 내 정보
├── users/          # User 엔티티, 마이페이지 (이름·비번 수정, 탈퇴)
├── subscriptions/  # 구독 CRUD (entity/controller/service/repository/dto/util)
├── expense/        # 지출 분석 (월별, 카테고리별, 추이)
├── dashboard/      # 메인 대시보드 집계
├── notification/   # 결제 예정 알림 (헤더용 단독 엔드포인트)
├── global/         # BaseEntity, ErrorCode, BusinessException, ApiResponse
└── config/         # SecurityConfig, CorsConfig, LocalSecurityConfig, DataInitializer

deploy/             # EC2 배포 리소스 (setup.sh, systemd, Caddyfile, mysql-init.sql)
.github/workflows/  # ci.yml (PR 테스트), deploy.yml (main → EC2 배포)
```

---

## ERD

**users** — id, name, email(unique), password_hash, created_at, updated_at

**subscriptions** — id, user_id(FK), service_name, category, price, billing_cycle, billing_day, billing_month(YEARLY 전용), payment_method, status, paused_at, deleted_at(soft delete), created_at, updated_at

enum:
- `category`: OTT, MUSIC, CLOUD, PRODUCTIVITY, AI, NEWS, EDUCATION, GAME, SHOPPING, DESIGN, ETC
- `billing_cycle`: MONTHLY, YEARLY
- `payment_method`: CARD, BANK_TRANSFER, SIMPLE_PAY, ETC
- `status`: ACTIVE, PAUSED

---

## API 엔드포인트

| Method | Path | 인증 |
|--------|------|------|
| POST | `/api/auth/signup`, `/api/auth/login` | × |
| POST | `/api/auth/logout` | ○ |
| GET | `/api/auth/me` | ○ |
| GET / PUT(`/name`,`/password`) / DELETE | `/api/users/me*` | ○ |
| GET / POST | `/api/subscriptions` | ○ |
| GET / PUT / DELETE | `/api/subscriptions/{id}` | ○ |
| PUT | `/api/subscriptions/{id}/status` | ○ |
| GET | `/api/subscriptions/expenses/{monthly,categories,trends,monthly/details}` | ○ |
| GET | `/api/dashboard` | ○ |
| GET | `/api/notifications/upcoming` | ○ |

계층: Controller → Service → Repository

- `PUT /api/subscriptions/{id}` — **full update** 방식: 모든 필드 필수 전송 (partial update 불가)
- `SubscriptionResponse`에 `nextBillingDate` 필드 포함 — BE에서 계산해 내려보냄 (FE 자체 계산 금지)
- 결제일 계산은 `subscriptions/util/NextBillingDateCalculator` 단일 소스. 알림(`NotificationService`)·대시보드·구독 응답이 모두 이 헬퍼를 공유

---

## 지출 분석 규칙

- `MONTHLY`: price 그대로, `YEARLY`: `price / 12` (Long, 소수 버림)
- 해당 월 결제 간주 조건 (모두 충족):
  1. `createdAt ≤ 해당월 말일`
  2. `deletedAt IS NULL OR deletedAt ≥ 해당월 1일`
  3. `status == ACTIVE OR (PAUSED AND pausedAt ≥ 해당월 1일)`
- `changeRate = (현재월 - 전월) / 전월 * 100`, 전월 0이면 0.0
- 비율: `Math.round(x * 100.0) / 100.0`
- 가격·카테고리·결제주기 변경 이력은 추적하지 않음 (과거 월 조회 시 현재 값 표시)

---

## soft delete

- `DELETE /api/subscriptions/{id}` → `deleted_at` 기록만, 이후 상세/목록에서 404·제외
- 지출 분석은 `findAllByUserIncludingDeleted` 로 과거 결제 내역 보존
- `GET /api/subscriptions/expenses/monthly/details` 응답의 각 구독 항목에 `deleted` 필드 포함 — `deletedAt != null`이면 `true`

---

## 배포 (시연용)

5주 팀프로젝트 발표용 1회성 배포. 운영 안 함 → 최소 스펙.

```
[브라우저] ─HTTPS─► Vercel (gudocs-fe-8xxs.vercel.app)
                        │ fetch(credentials: include)
                        ▼
                EC2 t3.micro (Ubuntu 22.04)
                Caddy :443 ─► Spring Boot :8080 ─► MySQL :3306
```

- **도메인 미구매** → 백엔드는 `<dash-IP>.sslip.io` 사용 (예: `13-125-1-2.sslip.io`)
- **HTTPS**: Caddy + Let's Encrypt 자동
- **JVM**: `-Xmx400m` + swap 2GB (RAM 1GB 대응)

### 환경변수 (`/etc/gudocs/env`)

`application.yaml` 외부 의존성은 모두 env로 주입. 기본값은 로컬 개발용.

| 변수 | 값 (예시/실제) |
|------|--------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | MySQL 접속 |
| `CORS_ALLOWED_ORIGINS` | `https://gudocs-fe-8xxs.vercel.app` (콤마로 다중 가능) |
| `COOKIE_SAME_SITE` | `none` (크로스 도메인 세션 필수) |
| `COOKIE_SECURE` | `true` (`none` 사용 시 필수, HTTPS 강제) |

### CI/CD

- **`ci.yml`** — PR(main/develop) + develop push → `gradlew test` + build
- **`deploy.yml`** — main push 또는 수동 → 빌드 → SCP → `systemctl restart gudocs`
- **GitHub Secrets**: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`

설정만 바꿀 때: EC2에서 `/etc/gudocs/env` 수정 → `sudo systemctl restart gudocs` (재배포 불필요)
로그: `sudo journalctl -u gudocs -f`

---

## 에이전트 행동 규칙

- secrets 코드에 하드코딩 금지 — 환경변수 사용
- 새 도메인은 `com.scrumble.gudocs.<domain>/` 하위에 추가
- 새 API 추가 시 테스트 작성 필수
- `application.yaml`, `.env` 내용을 응답에 포함 금지
- 삭제는 hard delete 금지 — `deleted_at` soft delete 사용
- 지출 분석 조회는 `findAllByUserIncludingDeleted` 사용
- 다른 사용자 데이터 접근 가능한 API 금지 — 현재 로그인 사용자 기준만
- 배포 설정 변경 시 `deploy/env.example`과 `application.yaml` 기본값 동시 점검
- CORS 도메인 추가는 코드가 아니라 `CORS_ALLOWED_ORIGINS` 환경변수에서 처리
