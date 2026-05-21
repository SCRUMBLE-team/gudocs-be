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

# 로컬 실행 (MySQL)
./gradlew bootRun

# 로컬 실행 (H2 인메모리 + mock data)
./gradlew bootRun --args='--spring.profiles.active=local'

# 전체 테스트
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "*ClassName"
```

---

## 현재 구조

```
src/main/java/com/scrumble/gudocs/
├── auth/                  # 회원가입, 로그인, 로그아웃, 내 정보
├── users/                 # User 엔티티, Repository
├── subscriptions/         # 구독 CRUD
│   ├── entity/            # Subscription, BillingCycle, SubscriptionCategory,
│   │                      #   SubscriptionStatus, PaymentMethod
│   ├── controller/        # SubscriptionController
│   ├── service/           # SubscriptionService
│   ├── repository/        # SubscriptionRepository
│   ├── dto/request/       # SubscriptionCreateRequest, UpdateRequest, StatusUpdateRequest
│   └── dto/response/      # SubscriptionResponse
├── expense/               # 지출 분석 독립 도메인
│   ├── controller/        # ExpenseController
│   ├── service/           # ExpenseService
│   └── dto/response/      # MonthlyExpenseResponse, CategoryExpenseResponse,
│                          #   ExpenseTrendResponse, MonthlyExpenseDetailResponse 외
├── dashboard/             # 메인 대시보드 데이터 조회
│   ├── controller/        # DashboardController
│   ├── service/           # DashboardService
│   └── dto/               # DashboardResponse, CategorySummary, UpcomingNotification
├── global/
│   ├── entity/            # BaseEntity (created_at, updated_at)
│   ├── exception/         # ErrorCode, BusinessException, GlobalExceptionHandler
│   └── response/          # ApiResponse
└── config/
    ├── SecurityConfig.java       # BCrypt, CORS(5173), 세션 인증
    ├── LocalSecurityConfig.java  # @Profile("local") — H2 콘솔 허용
    └── DataInitializer.java      # @Profile("local") — 앱 시작 시 mock data 삽입
```

**추가 예정 도메인:**
- `notification` — 결제 예정 알림 (7일·3일·1일 전)
- `member` — 회원 정보 수정, 비밀번호 변경, 탈퇴

---

## ERD

### users

| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | bigint | PK, auto increment |
| name | varchar | NOT NULL |
| email | varchar | NOT NULL, UNIQUE |
| password_hash | varchar | NOT NULL |
| created_at | datetime | NOT NULL |
| updated_at | datetime | - |

### subscriptions

| 컬럼 | 타입 | 제약 | 비고 |
|------|------|------|------|
| id | bigint | PK, auto increment | |
| user_id | bigint | FK(users.id), NOT NULL | |
| service_name | varchar | NOT NULL | |
| category | varchar | NOT NULL | enum: OTT, MUSIC, CLOUD, PRODUCTIVITY, AI, NEWS, EDUCATION, GAME, SHOPPING, DESIGN, ETC |
| price | bigint | NOT NULL | |
| billing_cycle | varchar | NOT NULL | enum: MONTHLY, YEARLY |
| billing_day | int | NOT NULL | |
| billing_month | int | - | YEARLY 전용 |
| payment_method | varchar | NOT NULL | enum: CARD, BANK_TRANSFER, SIMPLE_PAY, ETC |
| status | varchar | NOT NULL | enum: ACTIVE, PAUSED |
| paused_at | datetime | - | PAUSED 전환 시점 기록, RESUME 시 null |
| deleted_at | datetime | - | soft delete — null이면 활성, 값이 있으면 삭제됨 |
| created_at | datetime | NOT NULL | |
| updated_at | datetime | - | |

---

## API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/auth/signup` | 불필요 | 회원가입 |
| POST | `/api/auth/login` | 불필요 | 로그인 |
| POST | `/api/auth/logout` | 필요 | 로그아웃 |
| GET | `/api/auth/me` | 필요 | 내 정보 |
| GET | `/api/subscriptions` | 필요 | 구독 목록 (삭제된 것 제외) |
| POST | `/api/subscriptions` | 필요 | 구독 등록 |
| GET | `/api/subscriptions/{id}` | 필요 | 구독 상세 |
| PUT | `/api/subscriptions/{id}` | 필요 | 구독 수정 |
| DELETE | `/api/subscriptions/{id}` | 필요 | 구독 삭제 (soft delete) |
| PUT | `/api/subscriptions/{id}/status` | 필요 | 상태 변경 (ACTIVE/PAUSED) |
| GET | `/api/subscriptions/expenses/monthly` | 필요 | 월별 지출 분석 |
| GET | `/api/subscriptions/expenses/categories` | 필요 | 카테고리별 지출 분석 |
| GET | `/api/subscriptions/expenses/trends` | 필요 | 최근 6개월 지출 추이 |
| GET | `/api/subscriptions/expenses/monthly/details` | 필요 | 월별 상세 지출 내역 |
| GET | `/api/dashboard` | 필요 | 메인 대시보드 |

---

## 지출 분석 계산 규칙

- `MONTHLY` 구독: `price` 그대로 반영
- `YEARLY` 구독: `price / 12` (Long 나눗셈, 소수 버림)
- 해당 월에 결제했다고 간주하는 기준 (세 조건 모두 충족):
  1. `createdAt ≤ 해당월 말일` — 가입 이전 월은 제외
  2. `deletedAt IS NULL OR deletedAt ≥ 해당월 1일` — 삭제 이전 월은 포함, 그 다음 달부터 제외
  3. `status == ACTIVE OR (status == PAUSED AND pausedAt ≥ 해당월 1일)` — 정지 이전 월은 포함, 그 다음 달부터 제외
- `changeRate = (현재월 - 전월) / 전월 * 100`, 전월이 0원이면 `0.0`
- 비율 값: `Math.round(x * 100.0) / 100.0` (소수 둘째 자리)

### 알려진 한계

- 가격·카테고리·결제주기 변경 이력은 추적하지 않음 → 과거 월 조회 시 현재 값으로 표시됨
- PAUSE→RESUME→재PAUSE 시 마지막 정지 시점(`pausedAt`)만 보존됨

---

## soft delete 규칙

- `DELETE /api/subscriptions/{id}` 는 hard delete가 아니라 `deleted_at` 을 기록하는 soft delete
- 이후 `GET /api/subscriptions/{id}` 는 404 반환
- `GET /api/subscriptions` 목록에도 노출되지 않음
- 지출 분석 API는 `findAllByUserIncludingDeleted` 로 삭제된 구독의 과거 결제 내역을 보존

---

## API 계층 구조

Controller → Service → Repository

---

## 에이전트 행동 규칙

- secrets(DB 비밀번호, JWT Secret, API Key 등) 코드에 하드코딩 금지 — 환경변수 사용
- 새 도메인은 `com.scrumble.gudocs.<domain>/` 하위에 추가
- 새 API 추가 시 테스트 작성 필수
- `application.yaml`, `.env` 내용을 응답에 직접 포함하지 않음
- 삭제 기능 구현 시 hard delete가 아닌 soft delete 사용 (deleted_at 패턴)
- 지출 분석 관련 조회는 `findAllByUserIncludingDeleted` 사용 (과거 이력 보존)
