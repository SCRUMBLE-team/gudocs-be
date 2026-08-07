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
├── subscriptions/  # 구독 CRUD (entity/controller/service/repository/dto/util) + catalog/(서비스·요금제 카탈로그)
├── expense/        # 지출 분석 (월별, 카테고리별, 추이)
├── dashboard/      # 메인 대시보드 집계
├── notification/   # FCM Web Push (기기 등록 + 결제 예정 알림 스케줄러/발송)
├── ocr/            # CLOVA OCR 기반 구독 정보 스캔 (결제 알림/영수증 이미지 → 필드 파싱, 서비스 인식은 카탈로그 공유)
├── global/         # BaseEntity, ErrorCode, BusinessException, ApiResponse, security/(CurrentUserId)
└── config/         # SecurityConfig, WebConfig, CorsConfig, LocalSecurityConfig, DataInitializer, FirebaseConfig

src/main/resources/db/migration/  # Flyway 마이그레이션 (V1 baseline, V2 알림 dedup, V3 세션, V4 구독 service_code) — 앱 기동 시 자동 적용
deploy/             # EC2 배포 리소스 (setup.sh, systemd, Caddyfile, mysql-init.sql)
  migrations/       # (아카이브) Flyway 승격 이전 수동 실행 SQL — 신규 추가 금지 (README.md 참고)
.github/workflows/  # ci.yml (PR 테스트), deploy.yml (develop → EC2 배포)
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

**subscriptions** — id, user_id(FK), service_name, service_code(**nullable**), category, price, billing_cycle, first_billing_date(최초 결제일 앵커), status, paused_at, deleted_at(soft delete), created_at, updated_at
- `service_code`: 카탈로그 서비스의 불변 키(`ServiceCatalog.code`, 예 `NETFLIX`). **프론트가 로고를 찾는 기준**이다. 표시 이름은 오타 수정·브랜드 변경으로 바뀌므로 조인 키로 쓸 수 없다. 카탈로그에 없는 서비스를 직접 입력해 등록하면 null(로고 없음). 쓰기 시점에 카탈로그 존재 여부를 검증해 없는 코드면 400(`UNKNOWN_SERVICE_CODE`) — 저장돼 버리면 영영 로고를 못 찾기 때문. `V4__subscription_service_code.sql`
- `first_billing_date`: 다음 결제일 계산의 단일 기준 앵커. 기존 `billing_day`+`billing_month`를 통합. 다음 결제일은 저장하지 않고 `NextBillingDateCalculator`가 앵커+주기로 재계산(월말 드리프트 없음)

**push_registrations** — id, user_id(FK), fid, platform, device_name, enabled, last_registered_at, created_at, updated_at
- users 1:N. `UNIQUE(fid)` — 동일 fid 재등록 시 새 행 없이 소유자/상태 갱신. 해제는 hard delete가 아니라 `enabled=false`
- **소유권 이전은 의도된 정책**: fid는 Firebase Installation ID(브라우저 설치 1개)라, 공용 브라우저에서 다른 사용자가 같은 fid를 등록하면 현재 로그인 사용자로 소유권을 옮긴다. (기존 소유자 등록을 남기면 이전 사용자의 알림이 현재 사용자 브라우저로 전달되어 정보 노출 → `(user_id, fid)` 복합키 대신 전역 UNIQUE 유지)
- `fid` 전체 값은 로그에 남기지 않음(마스킹)

**user_notifications** — id, user_id, subscription_id(**nullable**), type, remind_offset, title, body, target_date, sent_at, created_at, updated_at
- 발송 이력 + 중복 방지. `UNIQUE(user_id, type, target_date, remind_offset)` — 같은 발송 단계 중복 발송 차단(다중 서버 대비 DB 제약으로 멱등). `sent_at`은 1건 이상 발송 성공 시 기록. userId/subscriptionId는 연관관계 아닌 값 컬럼
- `subscription_id`는 **nullable**: 결제 알림은 같은 결제일 구독을 묶어 1건 발송(특정 구독 없음), 검사 유도는 유저 단위라 둘 다 null
- `remind_offset`: 발송 단계 discriminator. 결제 알림의 결제 며칠 전(3=D-3, 0=당일)을 구분해 dedup 키에 포함(같은 결제일에 D-3/당일 두 번 발송을 서로 다른 건으로 취급). 단계 개념 없는 검사 유도는 0
- dedup 키 변경은 Flyway `V2__notification_dedup_userlevel.sql` (구 수동 `V20260804` 승계)

**spring_session / spring_session_attributes** — Spring Session JDBC 관리 테이블(직접 다루지 않음). 세션을 DB에 저장해 재배포/재기동에도 로그인 유지. `V3__spring_session.sql`로 생성. (아래 "세션 장기 유지" 참고)

enum:
- `provider`: GOOGLE, KAKAO, NAVER
- `category`: OTT, MUSIC, CLOUD, PRODUCTIVITY, AI, NEWS, EDUCATION, GAME, SHOPPING, DESIGN, ETC
- `billing_cycle`: MONTHLY, YEARLY
- `status`: ACTIVE, PAUSED
- `platform`(push): WEB
- `notification type`: BILLING_REMINDER(결제 예정), SUBSCRIPTION_REVIEW(구독 검사 유도)

---

## API 엔드포인트

| Method | Path | 인증 |
|--------|------|------|
| GET | `/oauth2/authorization/{google,kakao,naver}` | × |
| GET | `/login/oauth2/code/{provider}` (콜백, provider가 호출) | × |
| POST | `/api/auth/logout` | ○ |
| GET | `/api/auth/me` (로그인 상태·기본 정보 확인 — 내 정보 조회 단일 창구) | ○ |
| PUT(`/name`) / DELETE | `/api/users/me*` (온보딩 이름 입력/수정, 회원 탈퇴) | ○ |
| GET / POST | `/api/subscriptions` | ○ |
| GET / PUT / DELETE | `/api/subscriptions/{id}` | ○ |
| PUT | `/api/subscriptions/{id}/status` | ○ |
| GET | `/api/subscriptions/check-name?name=` (활성 구독 서비스명 중복 확인 — 경고 용도) | ○ |
| GET | `/api/subscriptions/catalog` (등록 화면용 서비스·요금제 목록) | ○ |
| GET | `/api/subscriptions/expenses/{monthly,categories,trends,monthly/details}` | ○ |
| GET | `/api/dashboard` | ○ |
| POST | `/api/push-registrations` (FCM 기기 등록 upsert) | ○ |
| DELETE | `/api/push-registrations/{registrationId}` (등록 해제 = enabled false, 멱등) | ○ |
| POST | `/api/push-registrations/test` (진단용 — 현재 사용자 활성 기기에 즉시 테스트 푸시, 기기별 결과 반환) | ○ |
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

## 서비스·요금제 카탈로그 (`subscriptions/catalog/ServiceCatalog`)

구독 등록 시 사용자가 결제 금액을 직접 입력하지 않도록, 서비스를 고르면 요금이 자동으로 채워지게 하는 데이터.

- **정적 상수 단일 소스**. 실시간 크롤링하지 않는다 — 서비스마다 파서가 필요하고, 상당수가 JS 렌더링·로그인·지역 뒤에 있으며, 파싱이 조용히 깨지면 사용자가 그 값을 저장해 지출 분석이 오염된다. 요금 변경은 **이 파일 수정 + 재배포**(국내 구독료는 연 1~2회 수준으로 변동).
- 요금은 `PRICES_CHECKED_ON` 시점에 공개 자료로 확인한 **국내 원화 정가**이고, API 응답에 이 날짜를 함께 내린다. 어디까지나 **기본값(참고값)** — 할인·프로모션·구 요금제 사용자가 있어 등록 화면에서 수정 가능하다.
- **신뢰할 만한 원화 가격을 못 찾으면 추측해 채우지 않고 `plans`를 빈 배열로 둔다** (해외 USD 결제라 원화 정가가 없는 서비스, 종료된 서비스, 구독이 아니라 건별 구매인 서비스). 프론트는 빈 배열이면 직접 입력 fallback.
- `aliases`는 OCR 매칭 전용 — `GET /api/subscriptions/catalog` 응답에는 내보내지 않는다.
- 이 카탈로그는 OCR(`ocr/parser/SubscriptionTextParser`)과 공유한다. 그래서 `ocr`이 아니라 `subscriptions` 하위에 둔다(`ocr` → `subscriptions` 단방향 의존).

### 데이터 주인과 FE 연결 (`code`)

```
ServiceCatalog.java (BE 단일 소스)
   ├── OCR 매칭 (ServiceCatalog.match)
   └── GET /api/subscriptions/catalog  ──code──►  FE: assets/logo/service/<CODE>.png
```

**서비스 데이터의 주인은 BE, 로고 이미지의 주인은 FE**이고 둘은 `code`로 연결한다. BE는 OCR 인식 때문에 목록을 못 버리고, FE는 번들링 때문에 PNG를 못 버리므로 이 경계가 최소 접점이다.

- **`code`(UPPER_SNAKE)는 불변이다.** `NETFLIX`, `YOUTUBE_PREMIUM` 처럼. FE 로고 파일명이 code라서 바꾸면 매칭이 깨진다. 형식·중복은 `ServiceCatalogTest`가 검사한다.
- **`canonicalName`은 순수 표시용**이라 오타 수정·브랜드 변경으로 자유롭게 고쳐도 된다. 예전엔 표시 이름이 조인 키여서 `"디지니플러스"` 오타를 고치자 FE 로고 매칭이 깨졌고, 그래서 `code`를 도입했다. **표시 이름을 키로 쓰지 말 것.**
- **`selectable=false`인 서비스는 신규 등록 대상이 아니다.** 등록 화면 선택지에서 빼고 **서버도 그 code 로 들어온 등록을 400(`SERVICE_NOT_SELECTABLE`)으로 거부한다** — FE 필터만으로는 직접 호출을 막을 수 없다. 다만 **OCR 매칭 대상으로는 남긴다**(과거 영수증을 계속 인식해야 하므로). 요금제는 두지 않는다.
  - 종료된 서비스: 클로바X(2026-04), SSG.COM 유니버스클럽(2026-01)
  - 살아 있지만 독립 구독 상품이 아닌 서비스: 쿠팡이츠(무료배달은 와우 멤버십 혜택)
  - 플래그를 `discontinued`가 아니라 `selectable` 로 둔 이유: 소비자가 필요한 정보는 "고를 수 있나" 하나이고, 종료·비독립상품이라는 *이유*는 코드 주석에 남기면 충분하다. 두 플래그를 두면 항상 같이 움직이는 값이 둘이 된다.
- **같은 금액을 두 서비스에 넣지 않는다.** 와우 멤버십 7,890원의 주인은 `COUPANG_WOW` 하나다. 쿠팡플레이·쿠팡이츠에도 같은 금액을 두면 사용자가 둘 다 등록해 **실제로 한 번 내는 돈이 지출에 두 번 잡힌다.** 쿠팡플레이는 와우와 별개로 결제하는 자기 상품(스포츠 패스)만 갖는다.
- FE에 로고 PNG가 없으면 `getServiceLogo()`가 `undefined`를 반환할 뿐 기능은 정상이다(자리표시자 처리). 서비스 추가 시 FE 로고는 여전히 수동이지만, 실패가 조용하지 않고 눈에 보인다.
- **저장된 구독도 code로 연결한다.** 카탈로그에만 code를 두면 `subscriptions`는 여전히 표시 이름만 갖고 있어서, 이름을 바꾸는 순간 기존 구독이 로고를 잃는다(= 애초의 문제 그대로). 그래서 `subscriptions.service_code`에 함께 저장하고 `SubscriptionResponse`로 내려준다. **프론트는 이름을 전혀 보지 않고 로고를 찾는다.**
- **서비스명을 내려주는 응답은 `serviceCode`도 함께 내려준다** — `SubscriptionResponse`, `SubscriptionExpenseDetail`, `DuplicateSubscriptionItem`, `UnusedSubscriptionCandidate`. 하나라도 빠지면 그 화면만 이름으로 code를 되찾는 폴백이 생겨 결국 이름 결합이 되살아난다. 서비스명을 노출하는 DTO를 새로 만들 때도 code를 같이 실을 것.
- **`serviceCode`가 있으면 `serviceName`은 서버가 카탈로그의 `canonicalName`으로 덮어쓴다.** 클라이언트 이름을 그대로 믿으면 `serviceCode=NETFLIX` + `serviceName="동네 헬스장"` 같은 모순된 행이 저장돼 목록엔 헬스장, 로고는 넷플릭스가 붙는다. 등록·수정이 같은 `resolveService()`를 쓴다. `category`는 덮어쓰지 않는다 — 이름·코드가 서비스의 *정체성*인 것과 달리 카테고리는 사용자가 자기 기준으로 분류하는 값이고, 기존 API도 임의 카테고리를 허용해 왔다.
- **중복 확인(`/check-name`)은 code 우선.** `code` 쿼리 파라미터를 주면 code 로, 없으면 기존대로 이름을 대소문자 무시 비교한다(직접 입력 서비스). 이름만 비교하면 같은 넷플릭스를 "Netflix"/"넷플릭스"로 두 번 등록해도 통과한다. `code`는 선택값이라 기존 호출은 그대로 동작한다.
- 등록/수정 요청의 `serviceCode`는 선택값이다 — 카탈로그에서 고른 서비스면 code를 그대로 전달하고, 직접 입력한 서비스면 생략한다. OCR 스캔 응답(`OcrSubscriptionResult.serviceCode`)도 code를 실어주므로 스캔 → 등록 흐름에서 그대로 넘기면 된다.

### 매칭 규칙 (OCR)

`ServiceCatalog.match()`는 완전 일치가 아니라 **등록된 이름이 OCR 텍스트에 부분 문자열로 들어있는지**를 본다(구두점·대소문자·공백 무시).

- **최장 매칭 우선** — 여러 서비스가 걸리면 매칭된 이름이 가장 긴 것을 고른다. 쿠팡 와우/플레이/이츠처럼 브랜드를 공유해도 선언 순서와 무관하게 가장 구체적인 것이 잡힌다. ("쿠팡" 같은 브랜드 단독 항목을 넣어 부분 매칭시키면 계열 서비스가 전부 뭉개지므로 금지)
- **짧은 ASCII alias는 단어 경계 확인** — `FLO`, `NYT` 등 3자 이하 영문 alias가 `workflow` 같은 무관한 단어 속에 우연히 겹치는 것을 막는다. 한글 짧은 이름(멜론·티빙·왓챠 …)은 조사·복합어가 띄어쓰기 없이 붙는 일이 흔해 이 규칙에서 제외한다.
- **요금제 역추적** — `CatalogService.planByPrice()`로 OCR 금액과 정확히 일치하는 요금제를 찾아 `OcrSubscriptionResult.planName`을 채운다. **OCR로 읽은 금액이 항상 우선**이고 카탈로그 정가로 덮어쓰지 않는다. 일치하는 요금제가 없으면 `planName`만 null.

---

## 세션 장기 유지 (Spring Session JDBC) + Flyway

- **세션 저장소 = DB**(`spring-session-jdbc`). 세션이 `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` 테이블에 저장돼 **재배포·재기동·다중 서버에도 로그인 유지**된다(기존 in-memory 세션은 재시작 시 소실됐음).
- TTL: `SESSION_TIMEOUT`(idle 만료, 기본 30d) + `SESSION_COOKIE_MAX_AGE`(쿠키 수명 = persistent cookie, 기본 30d). 둘 다 있어야 브라우저 종료 후에도 유지된다. 세션 인증 방식(`HttpSessionSecurityContextRepository`, `@CurrentUserId`)은 그대로 — 저장 위치만 DB로 바뀜.
- **스키마 = Flyway 단일 소스**. `ddl-auto=update` → **`validate`** 승격. 마이그레이션은 `src/main/resources/db/migration/`:
  - `V1__baseline.sql` — 승격 시점 운영 DB 스냅샷. 기존 운영 DB는 `flyway.baseline-on-migrate=true`+`baseline-version=1`로 V1을 봉인(미실행)하고 V2부터 적용. 빈 DB는 V1부터 전부 실행.
  - `V2__notification_dedup_userlevel.sql` — 구 수동 `V20260804` 승계 + `user_notifications.type` enum에 `SUBSCRIPTION_REVIEW` 추가(누락 시 검사 유도 알림 INSERT 실패하던 드리프트 정합화).
  - `V3__spring_session.sql` — 세션 테이블.
  - `V4__subscription_service_code.sql` — `subscriptions.service_code` 추가(nullable). 기존 행 백필 없음 — 승격 시점에 운영 데이터가 없었다.
- **배포**: Flyway가 앱 기동 시 자동 실행 → `systemctl restart gudocs` 만으로 마이그레이션 반영(수동 SSH SQL 불필요).
- **local/test**: H2라 `flyway.enabled=false` + `ddl-auto=create-drop` 유지(MySQL 방언 마이그레이션 미적용). 세션 테이블은 local은 `session.jdbc.initialize-schema=embedded`로 H2 자동 생성, **test는 `SessionAutoConfiguration` 제외**(테스트는 `MockHttpSession`에 SecurityContext를 직접 심어 인증 → Spring Session 필터가 켜지면 인증 유실). `spring.session.store-type`은 Boot 3.4+에서 제거된 프로퍼티라 무효.

---

## FCM Web Push (결제 예정 알림 + 구독 검사 유도)

알림 종류 2개. 실제 발송/중복방지/재시도/무효FID처리는 공통 `NotificationSender.send(userId, NotificationDraft)`에 위임(발송 로직 단일 소스). 각 배치 서비스는 "대상 선별 + draft 구성"만 담당한다.

- **① 결제 예정 알림 (`BILLING_REMINDER`)**: 스케줄러 → `NotificationDispatchService.dispatchDueReminders(today)` → 활성·미삭제 구독 조회(`SubscriptionRepository.findActiveForBillingReminder`, `JOIN FETCH user`) → `BillingReminderCalculator`로 **D-3·당일(offset {3,0})** 대상 필터(결제일 계산은 `NextBillingDateCalculator` 재사용) → **같은 유저의 같은 결제일 구독을 묶어** 알림 1건(`remind_offset`=결제 며칠 전)으로 `NotificationSender`에 전달. 제목/본문은 묶음 반영(예: "Netflix 외 1건 결제 예정" / "오늘 2건 27,900원이 결제될 예정이에요.")
- **② 구독 검사 유도 (`SUBSCRIPTION_REVIEW`)**: 스케줄러(별도 cron `FCM_REVIEW_CRON`) → `SubscriptionReviewDispatchService.dispatchDueReviews(today)` → 활성 기기를 가진 유저(`findDistinctUserIdsWithEnabledRegistration`) 대상으로, **회원 가입일(`User.createdAt`) 경과일**과 현재 구독 상태로 발송일 판정 → `NotificationSender`에 전달. 매일 스케줄러가 **그날의 현재 중복 상태**로 주기를 판정하므로 중복 해소 시 자동으로 4주 주기로 전환된다.
  - 같은 카테고리 활성 구독 2개 이상(중복) → 가입일로부터 **2주(14일)마다** 발송
  - 중복 없음(구독 0개 포함) → 가입일로부터 **4주(28일)마다** 발송
  - dedup: `(user_id, SUBSCRIPTION_REVIEW, target_date=발송일, remind_offset=0)` — 같은 날 재실행 멱등
- **NotificationSender 공통 처리**: `UserNotification` 저장(dedup 위반이면 skip/재사용) → 사용자 활성 `PushRegistration` 조회 → `PushSender`로 FID별 발송 → 성공 시 `sent_at` 기록, 무효 FID는 `enabled=false`
- **PushSender 추상화**: `FcmPushSender`(firebase enabled=true, Firebase Admin SDK) / `NoopPushSender`(비활성·기본, 실제 발송 안 함) — `@ConditionalOnProperty`로 택1. local/test는 Noop
- **트랜잭션 경계**: 발송 서비스는 클래스/메서드 `@Transactional` 없음 → repository 저장이 개별 커밋. 특정 기기 발송 실패(캐치)가 알림 이력이나 다른 기기 처리를 롤백하지 않음. 중복은 `user_notifications` UNIQUE 제약 + 삽입 시 `DataIntegrityViolationException` 캐치로 다중 서버 대응
- **FCM payload** — notification: `title`/`body`는 알림 종류·묶음 수에 따라 구성(위 참고) / data(모두 문자열): `type`(BILLING_REMINDER|SUBSCRIPTION_REVIEW), `link`. 클릭 이동 경로는 FE Service Worker가 `data.type`으로 분기(BILLING_REMINDER→`{FRONTEND_BASE_URL}/notifications` 알림함, SUBSCRIPTION_REVIEW→`{FRONTEND_BASE_URL}/subscriptions` 구독 점검). 묶음/유저 단위 알림이라 단일 `subscriptionId`는 싣지 않음
- **회원 탈퇴**: `UserService.deleteAccount`가 user 삭제 전에 `user_notifications`·`push_registrations`를 먼저 정리
- **발송 대상**: `fid`는 Firebase Installation ID. `firebase-admin` 9.10.0+의 `Message.Builder.setFid()`로 발송(구 `setToken`은 legacy registration token 호환용으로 deprecated → 미사용)
- **의존성**: `com.google.firebase:firebase-admin:9.10.0`. 크레덴셜은 `GOOGLE_APPLICATION_CREDENTIALS`(서비스 계정 JSON). 스키마는 Flyway 관리(아래 참고)
- **진단**: `POST /api/push-registrations/test`(`PushTestController` → `PushTestService`)는 스케줄러/중복이력 로직을 건너뛰고 현재 사용자 활성 기기에 `PushSender`로 즉시 테스트 푸시를 쏜다. 응답의 `senderType`(FcmPushSender/NoopPushSender)·기기별 결과(SUCCESS/INVALID_TOKEN/FAILED)로 `setFid` 실기기 도달 여부를 확인한다. `FIREBASE_ENABLED=false`면 Noop이라 항상 SUCCESS(실발송 아님)

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
| `FCM_REVIEW_CRON` | 구독 검사 유도 알림 스케줄러 cron (Asia/Seoul, 기본 `0 10 9 * * *`) |

- Google 콘솔 Authorized redirect URI: `<BE주소>/login/oauth2/code/google` (로컬·배포 각각 등록)

### CI/CD

- **`ci.yml`** — PR(main/develop) + develop push → `gradlew test` + build
- **`deploy.yml`** — develop CI(`ci.yml`)가 **success로 완료**됐을 때(`workflow_run`) 또는 수동 → 빌드 → SCP → `/etc/gudocs/env` 렌더링 → `systemctl restart gudocs`. 즉 테스트 통과 후에만 배포되며, CI를 통과한 그 커밋(`workflow_run.head_sha`)을 체크아웃한다. (PR CI·실패 CI는 배포 안 함)
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
- 스키마 변경은 Flyway 마이그레이션(`src/main/resources/db/migration/V<n>__<설명>.sql`)으로 작성 — 앱 기동 시 자동 적용. `ddl-auto=validate`라 엔티티와 스키마가 어긋나면 기동 실패로 조기 감지됨. (`deploy/migrations/`는 승격 이전 아카이브, 신규 추가 금지)
