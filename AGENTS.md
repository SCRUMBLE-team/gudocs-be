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
├── notification/   # FCM Web Push (기기 등록 + 결제 예정 알림 스케줄러/발송)
├── ocr/            # CLOVA OCR 기반 구독 정보 스캔 (결제 알림/영수증 이미지 → 필드 파싱)
├── global/         # BaseEntity, ErrorCode, BusinessException, ApiResponse, security/(CurrentUserId)
└── config/         # SecurityConfig, WebConfig, CorsConfig, LocalSecurityConfig, DataInitializer, FirebaseConfig

deploy/             # EC2 배포 리소스 (setup.sh, systemd, Caddyfile, mysql-init.sql)
  migrations/       # 스키마 마이그레이션 버전 파일 + 적용 이력 (README.md 참고)
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

**push_registrations** — id, user_id(FK), fid, platform, device_name, enabled, last_registered_at, created_at, updated_at
- users 1:N. `UNIQUE(fid)` — 동일 fid 재등록 시 새 행 없이 소유자/상태 갱신. 해제는 hard delete가 아니라 `enabled=false`
- **소유권 이전은 의도된 정책**: fid는 Firebase Installation ID(브라우저 설치 1개)라, 공용 브라우저에서 다른 사용자가 같은 fid를 등록하면 현재 로그인 사용자로 소유권을 옮긴다. (기존 소유자 등록을 남기면 이전 사용자의 알림이 현재 사용자 브라우저로 전달되어 정보 노출 → `(user_id, fid)` 복합키 대신 전역 UNIQUE 유지)
- `fid` 전체 값은 로그에 남기지 않음(마스킹)

**user_notifications** — id, user_id, subscription_id, type, title, body, target_date, sent_at, created_at, updated_at
- 발송 이력 + 중복 방지. `UNIQUE(user_id, subscription_id, type, target_date)` — 같은 결제 예정일 중복 발송 차단(다중 서버 대비 DB 제약으로 멱등). `sent_at`은 1건 이상 발송 성공 시 기록. userId/subscriptionId는 연관관계 아닌 값 컬럼

enum:
- `provider`: GOOGLE, KAKAO, NAVER
- `category`: OTT, MUSIC, CLOUD, PRODUCTIVITY, AI, NEWS, EDUCATION, GAME, SHOPPING, DESIGN, ETC
- `billing_cycle`: MONTHLY, YEARLY
- `status`: ACTIVE, PAUSED
- `platform`(push): WEB
- `notification type`: BILLING_REMINDER

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
| POST | `/api/push-registrations` (FCM 기기 등록 upsert) | ○ |
| DELETE | `/api/push-registrations/{registrationId}` (등록 해제 = enabled false, 멱등) | ○ |
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
- 결제일 계산은 `subscriptions/util/NextBillingDateCalculator` 단일 소스. 대시보드·구독 응답·알림 배치가 모두 이 헬퍼를 공유

---

## FCM Web Push (결제 예정 알림)

- **발송 흐름**: 스케줄러(`NotificationScheduler`, `@ConditionalOnProperty(app.firebase.enabled=true)`) → `NotificationDispatchService.dispatchDueReminders(today)` → ① 활성·미삭제 구독 조회(`SubscriptionRepository.findActiveForBillingReminder`, `JOIN FETCH user`) → ② `BillingReminderCalculator`로 7일 이내 대상 필터(결제일 계산은 `NextBillingDateCalculator` 재사용, SQL로 표현 불가) → ③ `UserNotification` 저장(중복이면 skip) → ④ 사용자 활성 `PushRegistration` 조회 → ⑤ `PushSender`로 FID별 발송 → ⑥ 성공 시 `sent_at` 기록, 무효 FID는 `enabled=false`
- **PushSender 추상화**: `FcmPushSender`(firebase enabled=true, Firebase Admin SDK) / `NoopPushSender`(비활성·기본, 실제 발송 안 함) — `@ConditionalOnProperty`로 택1. local/test는 Noop
- **트랜잭션 경계**: 발송 서비스는 클래스/메서드 `@Transactional` 없음 → repository 저장이 개별 커밋. 특정 기기 발송 실패(캐치)가 알림 이력이나 다른 기기 처리를 롤백하지 않음. 중복은 `user_notifications` UNIQUE 제약 + 삽입 시 `DataIntegrityViolationException` 캐치로 다중 서버 대응
- **FCM payload** — notification: `title="{서비스명} 결제 예정"`, `body="{N}일 후 {금액}원이 결제될 예정이에요."` / data(모두 문자열): `type=BILLING_REMINDER`, `subscriptionId`, `link={FRONTEND_BASE_URL}/subscriptions/{subscriptionId}`
- **회원 탈퇴**: `UserService.deleteAccount`가 user 삭제 전에 `user_notifications`·`push_registrations`를 먼저 정리
- **발송 대상**: `fid`는 Firebase Installation ID. `firebase-admin` 9.10.0+의 `Message.Builder.setFid()`로 발송(구 `setToken`은 legacy registration token 호환용으로 deprecated → 미사용)
- **의존성**: `com.google.firebase:firebase-admin:9.10.0`. 크레덴셜은 `GOOGLE_APPLICATION_CREDENTIALS`(서비스 계정 JSON). 신규 테이블은 `ddl-auto=update`로 생성(Flyway 미도입)

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
| `FIREBASE_ENABLED` | `true`면 Firebase Admin 초기화 + FCM 발송 + 알림 스케줄러 활성화 (기본 false) |
| `GOOGLE_APPLICATION_CREDENTIALS` | Firebase 서비스 계정 JSON 경로 (예: `/etc/gudocs/firebase-service-account.json`) |
| `FRONTEND_BASE_URL` | 알림 클릭 이동 URL 기준 (예: `https://gudocs-fe-v2.vercel.app`) |
| `FCM_NOTIFICATION_CRON` | 결제 예정 알림 스케줄러 cron (Asia/Seoul, 기본 `0 0 9 * * *`) |

- Google 콘솔 Authorized redirect URI: `<BE주소>/login/oauth2/code/google` (로컬·배포 각각 등록)

### CI/CD

- **`ci.yml`** — PR(main/develop) + develop push → `gradlew test` + build
- **`deploy.yml`** — main push 또는 수동 → 빌드 → SCP → `/etc/gudocs/env` 렌더링 → `systemctl restart gudocs`
  - FCM: `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64`(서비스 계정 JSON을 base64 인코딩) 시크릿이 있으면 디코드해 `/etc/gudocs/firebase-service-account.json`으로 렌더링하고 `FIREBASE_ENABLED=true`로 켠다. 시크릿이 없으면 `false`(파일 없이 켜면 기동 실패하므로 안전 가드). JSON은 멀티라인/특수문자가 많아 raw 대신 base64로 전달
- **GitHub Secrets**: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64`(선택 — 미설정 시 FCM 비활성)

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
- 컬럼 삭제·타입/제약 변경(ddl-auto=update가 반영 못 하는 스키마 변경)은 `deploy/migrations/`에 `V<YYYYMMDD>__<설명>.sql`로 남기고 README 이력 갱신
