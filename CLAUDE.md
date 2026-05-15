# CLAUDE.md — gudocs-be

@AGENTS.md

구독 서비스 통합 관리 대시보드 백엔드. Spring Boot 3.5 / Java 21 / MySQL.

---

## 핵심 명령어

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

## 패키지 구조

패키지 루트: `com.scrumble.gudocs`

```
config/
  SecurityConfig.java         # BCrypt, CORS(5173), 세션 인증
  LocalSecurityConfig.java    # @Profile("local") — H2 콘솔 허용
  DataInitializer.java        # @Profile("local") — 앱 시작 시 mock data 삽입

auth/
  controller/AuthController.java
  service/AuthService.java       # UserDetailsService 구현
  dto/LoginRequest, SignupRequest, UserResponse

users/
  entity/User.java               # id, name, email, password_hash
  repository/UserRepository.java # findByEmail, existsByEmail

subscriptions/
  entity/
    Subscription.java            # id, user_id, service_name, category, price,
                                 # billing_cycle, billing_day, billing_month,
                                 # payment_method, status
    BillingCycle.java            # MONTHLY, YEARLY
    SubscriptionCategory.java    # OTT, MUSIC, CLOUD, PRODUCTIVITY, AI, NEWS,
                                 #   EDUCATION, GAME, SHOPPING, DESIGN, ETC
    SubscriptionStatus.java      # ACTIVE, PAUSED
    PaymentMethod.java           # CARD, BANK_TRANSFER, SIMPLE_PAY, ETC
  controller/SubscriptionController.java
  service/SubscriptionService.java
  repository/SubscriptionRepository.java  # findAllByUserOrderByCreatedAtDesc

global/
  entity/BaseEntity.java         # created_at, updated_at (JPA Auditing)
  exception/ErrorCode.java       # CONFLICT, UNAUTHORIZED, NOT_FOUND, FORBIDDEN, BAD_REQUEST
  exception/BusinessException.java
  exception/GlobalExceptionHandler.java
  response/ApiResponse.java      # record: success, message, data
```

---

## API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/auth/signup` | 불필요 | 회원가입 |
| POST | `/api/auth/login` | 불필요 | 로그인 |
| POST | `/api/auth/logout` | 필요 | 로그아웃 |
| GET | `/api/auth/me` | 필요 | 내 정보 |
| GET | `/api/subscriptions` | 필요 | 구독 목록 조회 |
| POST | `/api/subscriptions` | 필요 | 구독 등록 |
| PUT | `/api/subscriptions/{id}` | 필요 | 구독 수정 |
| DELETE | `/api/subscriptions/{id}` | 필요 | 구독 삭제 |
| PATCH | `/api/subscriptions/{id}/status` | 필요 | 상태 변경 (ACTIVE/PAUSED) |

---

## 리소스 파일

| 파일 | 프로파일 | 설명 |
|------|----------|------|
| `src/main/resources/application.yaml` | 기본 | MySQL 연결 |
| `src/main/resources/application-local.yaml` | local | H2 인메모리, H2 콘솔(`/h2-console`) |
| `src/test/resources/application.yaml` | test | H2 인메모리, create-drop |

**local 프로파일 mock 계정**
- email: `test@test.com` / password: `Test1234!`
- 구독 8개 자동 삽입 (Netflix, YouTube Premium, Spotify, iCloud+, Google One, ChatGPT Plus, Adobe CC, 인프런)

---

## 작업 규칙

- 새 도메인 추가 시 `com.scrumble.gudocs.<domain>/` 하위에 Controller, Service, Repository 생성
- 새 API 추가 시 테스트 클래스도 함께 작성

## 브랜치 & 커밋

- 브랜치: `feat/<이슈번호>-<설명>`, `fix/<이슈번호>-<설명>`
- 커밋 메시지: 한국어, `feat: 회원가입 API 구현` 형식
- PR 본문에 관련 이슈 번호 연결

---

## 절대 하지 말 것

- secrets(DB 비밀번호, JWT Secret 등) 코드 또는 응답에 노출
- `application.yaml` 내용을 응답에 직접 포함
