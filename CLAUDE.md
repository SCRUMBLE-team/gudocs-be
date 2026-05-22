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
````

---

## 패키지 구조

패키지 루트: `com.scrumble.gudocs`

```text
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
  controller/UserController.java # 마이페이지 사용자 정보 조회/수정/탈퇴
  service/UserService.java
  dto/
    UserInfoResponse.java
    UserNameUpdateRequest.java
    UserPasswordUpdateRequest.java
    UserDeleteRequest.java

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

| 메서드    | 경로                               | 인증  | 설명                    |
| ------ | -------------------------------- | --- | --------------------- |
| POST   | `/api/auth/signup`               | 불필요 | 회원가입                  |
| POST   | `/api/auth/login`                | 불필요 | 로그인                   |
| POST   | `/api/auth/logout`               | 필요  | 로그아웃                  |
| GET    | `/api/auth/me`                   | 필요  | 내 정보                  |
| GET    | `/api/users/me`                  | 필요  | 마이페이지 내 정보 조회         |
| PUT    | `/api/users/me/name`             | 필요  | 이름 수정                 |
| PUT    | `/api/users/me/password`         | 필요  | 비밀번호 수정               |
| DELETE | `/api/users/me`                  | 필요  | 회원 탈퇴                 |
| GET    | `/api/subscriptions`             | 필요  | 구독 목록 조회              |
| POST   | `/api/subscriptions`             | 필요  | 구독 등록                 |
| PUT    | `/api/subscriptions/{id}`        | 필요  | 구독 수정                 |
| DELETE | `/api/subscriptions/{id}`        | 필요  | 구독 삭제                 |
| PUT    | `/api/subscriptions/{id}/status` | 필요  | 상태 변경 (ACTIVE/PAUSED) |

---

## MyPage / User API 요구사항

마이페이지 기능은 인증 도메인이 아니라 `users` 도메인에서 구현한다.
회원가입, 로그인, 로그아웃은 `auth` 도메인에 속하고, 마이페이지의 사용자 정보 조회/수정/탈퇴는 `users` 도메인에 속한다.

### 구현 대상 API

| 기능      | Method | Path                     |
| ------- | ------ | ------------------------ |
| 내 정보 조회 | GET    | `/api/users/me`          |
| 이름 수정   | PUT    | `/api/users/me/name`     |
| 비밀번호 수정 | PUT    | `/api/users/me/password` |
| 회원 탈퇴   | DELETE | `/api/users/me`          |

### 공통 요구사항

* 모든 MyPage/User API는 로그인 세션 쿠키가 필요하다.
* 현재 로그인한 사용자 기준으로만 처리한다.
* 현재 로그인한 사용자 조회 방식은 기존 `auth` 구현 방식을 따른다.
* Controller에서는 기존 방식처럼 `@AuthenticationPrincipal UserDetails userDetails`를 사용한다.
* `userDetails.getUsername()`으로 이메일을 얻고, `UserRepository.findByEmail()`로 사용자 엔티티를 조회한다.
* 기존 `ApiResponse`, `BusinessException`, `ErrorCode`, `GlobalExceptionHandler` 구조를 따른다.
* 기존 인증 구조, SecurityConfig, 세션 구조를 임의로 변경하지 않는다.
* DTO는 기존 프로젝트 스타일에 맞춰 Java `record`를 우선 사용한다.
* Request DTO에는 Bean Validation을 적용한다.
* 새 API 추가 시 테스트 클래스도 함께 작성한다.

---

## MyPage / User API 상세 명세

### 1. 내 정보 조회 API

```http
GET /api/users/me
```

#### Request

로그인 세션 쿠키가 필요하다.
Request body는 없다.

#### Response

```json
{
  "success": true,
  "message": "내 정보 조회에 성공했습니다.",
  "data": {
    "userId": 1,
    "name": "이성아",
    "email": "test@example.com"
  }
}
```

#### Error

* `401 Unauthorized`: 로그인 세션이 없거나 만료된 경우
* `404 Not Found`: 사용자를 찾을 수 없는 경우

---

### 2. 이름 수정 API

```http
PUT /api/users/me/name
```

#### Request

로그인 세션 쿠키가 필요하다.

```json
{
  "name": "이성아"
}
```

#### Response

```json
{
  "success": true,
  "message": "이름이 수정되었습니다.",
  "data": {
    "userId": 1,
    "name": "이성아",
    "email": "test@example.com"
  }
}
```

#### Validation

* 이름이 누락되면 `400 Bad Request`
* 이름이 공백이면 `400 Bad Request`

#### Error

* `401 Unauthorized`: 로그인 세션이 없거나 만료된 경우
* `404 Not Found`: 사용자를 찾을 수 없는 경우

---

### 3. 비밀번호 수정 API

```http
PUT /api/users/me/password
```

#### Request

로그인 세션 쿠키가 필요하다.
비밀번호 수정 시에는 현재 비밀번호와 새 비밀번호를 모두 입력받는다.

```json
{
  "currentPassword": "Test1234!",
  "newPassword": "NewTest1234!"
}
```

#### Response

```json
{
  "success": true,
  "message": "비밀번호가 수정되었습니다.",
  "data": null
}
```

#### Validation

* 현재 비밀번호가 누락되면 `400 Bad Request`
* 새 비밀번호가 누락되면 `400 Bad Request`
* 새 비밀번호가 비밀번호 정책에 맞지 않으면 `400 Bad Request`
* 현재 비밀번호가 일치하지 않으면 `400 Bad Request`
* 현재 비밀번호와 새 비밀번호가 같으면 `400 Bad Request`

#### 구현 규칙

* 현재 비밀번호 검증은 `PasswordEncoder.matches(currentPassword, user.getPassword())` 방식으로 처리한다.
* 새 비밀번호는 `PasswordEncoder.encode(newPassword)`로 암호화하여 저장한다.
* 비밀번호 변경 후에도 기존 세션을 유지할지, 로그아웃 처리할지는 기존 프로젝트 정책을 따른다.
* 별도 정책이 없다면 비밀번호 변경 후 세션은 유지한다.

#### Error

* `401 Unauthorized`: 로그인 세션이 없거나 만료된 경우
* `404 Not Found`: 사용자를 찾을 수 없는 경우

---

### 4. 회원 탈퇴 API

```http
DELETE /api/users/me
```

#### Request

로그인 세션 쿠키가 필요하다.
회원 탈퇴 시 현재 비밀번호를 입력받아 본인 확인을 한다.

```json
{
  "currentPassword": "Test1234!"
}
```

#### Response

```json
{
  "success": true,
  "message": "회원 탈퇴가 완료되었습니다.",
  "data": null
}
```

#### Validation

* 현재 비밀번호가 누락되면 `400 Bad Request`
* 현재 비밀번호가 일치하지 않으면 `400 Bad Request`

#### 구현 규칙

* 현재 비밀번호가 일치하는 경우에만 회원 탈퇴를 처리한다.
* 회원 탈퇴 시 해당 사용자의 구독 정보도 함께 삭제한다.
* 구독 정보를 먼저 삭제한 뒤 사용자 정보를 삭제한다.
* 회원 탈퇴 완료 후 현재 세션을 무효화한다.
* `SubscriptionRepository`에 사용자 기준 삭제 메서드를 추가할 수 있다.

  * 예: `void deleteAllByUser(User user);`
  * 또는 `void deleteAllByUserId(Long userId);`

#### Error

* `401 Unauthorized`: 로그인 세션이 없거나 만료된 경우
* `404 Not Found`: 사용자를 찾을 수 없는 경우

---

## User 관련 ErrorCode 추가 규칙

기존 `ErrorCode` enum 구조를 유지하며 필요한 예외 코드를 추가한다.

추가 후보:

```text
USER_NOT_FOUND
INVALID_PASSWORD
SAME_AS_OLD_PASSWORD
```

단, 이미 동일 의미의 ErrorCode가 존재한다면 새로 만들지 말고 기존 코드를 재사용한다.

예상 메시지:

| ErrorCode            | Status          | Message                   |
| -------------------- | --------------- | ------------------------- |
| USER_NOT_FOUND       | 404 Not Found   | 사용자를 찾을 수 없습니다.           |
| INVALID_PASSWORD     | 400 Bad Request | 현재 비밀번호가 일치하지 않습니다.       |
| SAME_AS_OLD_PASSWORD | 400 Bad Request | 새 비밀번호는 현재 비밀번호와 달라야 합니다. |

---

## 리소스 파일

| 파일                                          | 프로파일  | 설명                            |
| ------------------------------------------- | ----- | ----------------------------- |
| `src/main/resources/application.yaml`       | 기본    | MySQL 연결                      |
| `src/main/resources/application-local.yaml` | local | H2 인메모리, H2 콘솔(`/h2-console`) |
| `src/test/resources/application.yaml`       | test  | H2 인메모리, create-drop          |

**local 프로파일 mock 계정**

* email: `test@test.com` / password: `Test1234!`
* 구독 8개 자동 삽입 (Netflix, YouTube Premium, Spotify, iCloud+, Google One, ChatGPT Plus, Adobe CC, 인프런)

---

## 작업 규칙

* 새 도메인 추가 시 `com.scrumble.gudocs.<domain>/` 하위에 Controller, Service, Repository 생성
* 단, 마이페이지 기능은 새 도메인을 만들지 않고 기존 `users` 도메인에 Controller, Service, DTO를 추가한다.
* 새 API 추가 시 테스트 클래스도 함께 작성
* 수정성 API는 `PATCH`가 아니라 `PUT`을 사용한다.
* 기존 `auth`, `users`, `subscriptions` 코드 스타일과 응답 포맷을 우선적으로 따른다.
* 인증이 필요한 API는 기존 세션 인증 방식을 그대로 사용한다.
* 현재 로그인한 사용자 기준으로만 데이터를 조회/수정/삭제한다.
* 기존 인증 구조를 임의로 변경하지 않는다.

## 브랜치 & 커밋

* 브랜치: `feat/<이슈번호>-<설명>`, `fix/<이슈번호>-<설명>`
* 커밋 메시지: 한국어, `feat: 회원가입 API 구현` 형식
* PR 본문에 관련 이슈 번호 연결

---

## 절대 하지 말 것

* secrets(DB 비밀번호, JWT Secret 등) 코드 또는 응답에 노출
* `application.yaml` 내용을 응답에 직접 포함
* 기존 인증 구조, SecurityConfig, 세션 정책을 임의로 변경
* 비밀번호를 평문으로 저장
* 비밀번호를 응답 데이터에 포함
* 다른 사용자의 정보를 조회, 수정, 삭제할 수 있는 API 구현
* 회원 탈퇴 시 구독 정보가 남도록 구현