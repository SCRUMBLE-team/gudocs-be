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
├── auth/           # 소셜 로그인(oauth/), 로그아웃, 내 정보
├── users/          # User·SocialAccount 엔티티, 마이페이지 (이름 수정, 탈퇴)
├── subscriptions/  # 구독 CRUD (entity/controller/service/repository/dto/util)
├── expense/        # 지출 분석 (월별, 카테고리별, 추이)
├── dashboard/      # 메인 대시보드 집계
├── notification/   # 결제 예정 알림 (헤더용 단독 엔드포인트)
├── ocr/            # CLOVA OCR 기반 구독 정보 스캔 (결제 알림/영수증 이미지 → 필드 파싱)
├── global/         # BaseEntity, ErrorCode, BusinessException, ApiResponse, security/(CurrentUserId)
└── config/         # SecurityConfig, WebConfig, CorsConfig, LocalSecurityConfig, DataInitializer

deploy/             # EC2 배포 리소스 (setup.sh, systemd, Caddyfile, mysql-init.sql)
.github/workflows/  # ci.yml (PR 테스트), deploy.yml (main → EC2 배포)
```

---

## ERD

**users** — id, name(nullable), email(**unique 아님**), created_at, updated_at
- 소셜 로그인 전용 전환으로 `password_hash` **제거**
- `name`은 최초 로그인 시 null → 온보딩(이름 입력) 화면에서 `PUT /api/users/me/name`로 채움
- 식별은 provider+providerId → 다른 제공자가 같은 이메일이면 **별도 회원** (email unique 아님)

**social_accounts** — id, user_id(FK), provider, provider_id, email, email_verified, last_login_at, created_at, updated_at
- users 1:N social_accounts
- `UNIQUE(provider, provider_id)` — 로그인 조회키
- `UNIQUE(user_id, provider)` — 같은 provider 중복 연결 금지

**subscriptions** — id, user_id(FK), service_name, category, price, billing_cycle, first_billing_date(최초 결제일 앵커), status, paused_at, deleted_at(soft delete), created_at, updated_at
- `first_billing_date`: 다음 결제일 계산의 단일 기준 앵커. 기존 `billing_day`+`billing_month`를 통합. 다음 결제일은 저장하지 않고 `NextBillingDateCalculator`가 앵커+주기로 재계산(월말 드리프트 없음)

enum:
- `provider`: GOOGLE, KAKAO, NAVER
- `category`: OTT, MUSIC, CLOUD, PRODUCTIVITY, AI, NEWS, EDUCATION, GAME, SHOPPING, DESIGN, ETC
- `billing_cycle`: MONTHLY, YEARLY
- `status`: ACTIVE, PAUSED

---

## API 엔드포인트

| Method | Path | 인증 |
|--------|------|------|
| GET | `/oauth2/authorization/{google,kakao,naver}` | × |
| GET | `/login/oauth2/code/{provider}` (콜백, provider가 호출) | × |
| POST | `/api/auth/logout` | ○ |
| GET | `/api/auth/me` | ○ | (로그인 상태·기본 정보 확인 — 내 정보 조회 단일 창구)
| PUT(`/name`) / DELETE | `/api/users/me*` | ○ | (온보딩 이름 입력/수정, 회원 탈퇴)
| GET / POST | `/api/subscriptions` | ○ |
| GET / PUT / DELETE | `/api/subscriptions/{id}` | ○ |
| PUT | `/api/subscriptions/{id}/status` | ○ |
| GET | `/api/subscriptions/expenses/{monthly,categories,trends,monthly/details}` | ○ |
| GET | `/api/dashboard` | ○ |
| GET | `/api/notifications/upcoming` | ○ |
| POST | `/api/ocr/subscriptions/scan` | ○ |

계층: Controller → Service → Repository

### 인증 (소셜 로그인 전용)

- **이메일/비밀번호 로그인 폐지** — `signup`/`login` 엔드포인트, `UserDetailsService`, BCrypt 모두 제거
- Spring Security `oauth2Login` 사용. `/oauth2/authorization/{provider}` 2개(authorization/callback)는 프레임워크 자동 노출, 커스텀 REST 컨트롤러 없음
- `CustomOAuth2UserService`: provider userinfo → `OAuth2UserInfo`로 정규화(3사 switch 분기) → `(provider, provider_id)`로 조회, 없으면 신규 User+SocialAccount 생성
- **provider+providerId로 식별**: 다른 소셜 제공자로 로그인하면 같은 이메일이라도 **별도 회원으로 가입**(자동 병합·이메일 충돌 차단 없음)
- 세션 principal = `CustomOAuth2User`(user.id 보유). 컨트롤러는 `@CurrentUserId Long userId`로 주입받음 (`CurrentUserIdArgumentResolver`, `WebConfig` 등록)
- 로그인 성공 시 `app.oauth.success-redirect`(env `OAUTH_SUCCESS_REDIRECT`)로 리다이렉트
  - **신규 유저(name null)** → `?onboarding=1` 붙여 리다이렉트 → FE는 이름 입력 화면 표시 후 `PUT /api/users/me/name` 호출
  - 기존 유저 → 파라미터 없이 리다이렉트
- 로그인 실패 시 `?login=fail&code=OAUTH_LOGIN_FAILED`로만 리다이렉트(예외 메시지는 URL에 노출 안 함, 원인은 서버 로그). `OAuth2LoginFailureHandler`가 처리
- 미인증 요청은 `authenticationEntryPoint`가 **401 + `ApiResponse` JSON**(`{success:false, message:"로그인이 필요합니다.", data:null}`) 반환
- 회원 탈퇴는 세션 인증만으로 처리(비번 확인 없음), 탈퇴 시 subscriptions·social_accounts 함께 삭제
- 내 정보 조회는 `GET /api/auth/me` 하나로 통일(`GET /api/users/me`는 제거)
- Swagger: `@CurrentUserId`는 `SpringDocUtils.addAnnotationsToIgnore` + 메타 `@Parameter(hidden)`로 문서에서 숨김. API 인터페이스의 요청 Body 파라미터에는 `@RequestBody`를 명시해야 Swagger가 requestBody로 인식

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
[브라우저] ─HTTPS─► Vercel (gudocs-fe-v2.vercel.app)
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
| `CORS_ALLOWED_ORIGINS` | `https://gudocs-fe-v2.vercel.app` (콤마로 다중 가능) |
| `COOKIE_SAME_SITE` | `none` (크로스 도메인 세션 필수) |
| `COOKIE_SECURE` | `true` (`none` 사용 시 필수, HTTPS 강제) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth 크레덴셜 (Google Cloud Console 발급) |
| `OAUTH_SUCCESS_REDIRECT` | 소셜 로그인 성공 후 리다이렉트할 프론트 주소 |
| `CLOVA_OCR_INVOKE_URL` | CLOVA OCR General API Invoke URL (Naver Cloud Platform 콘솔 발급) |
| `CLOVA_OCR_SECRET_KEY` | CLOVA OCR Secret Key (Naver Cloud Platform 콘솔 발급) |

- Google 콘솔 Authorized redirect URI: `<BE주소>/login/oauth2/code/google` (로컬·배포 각각 등록)

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
- 다른 사용자 데이터 접근 가능한 API 금지 — 현재 로그인 사용자 기준만 (`@CurrentUserId Long userId`)
- 배포 설정 변경 시 `deploy/env.example`과 `application.yaml` 기본값 동시 점검
- CORS 도메인 추가는 코드가 아니라 `CORS_ALLOWED_ORIGINS` 환경변수에서 처리
