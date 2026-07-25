# CLOVA OCR 기반 구독 등록 보조 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 결제 알림 캡처/영수증/구독 화면 이미지를 업로드하면 CLOVA OCR로 텍스트를 추출하고, 구독 등록 필드(서비스명/카테고리/금액/결제주기/최초결제일/결제수단)를 best-effort로 파싱해 반환하는 `POST /api/ocr/subscriptions/scan` API를 추가한다.

**Architecture:** 새 도메인 `com.scrumble.gudocs.ocr/`. `OcrController` → `OcrService`(검증 + 오케스트레이션) → `ClovaOcrClient`(외부 API 호출, interface로 분리해 mock 가능) → `SubscriptionTextParser`(순수 함수, OCR 텍스트 → 필드 파싱) → `KnownServiceRegistry`(서비스명 canonical name/alias → 카테고리 매핑 정적 테이블). 응답은 전 필드 nullable인 `OcrSubscriptionResult`. 이미지는 저장하지 않는다.

**Tech Stack:** Spring Boot 3.5 / Java 21, Spring MVC(MultipartFile), Spring `RestClient`(신규 외부 HTTP 호출, 별도 의존성 추가 없음), Jackson record 역직렬화, JUnit5 + Mockito + AssertJ.

## Global Constraints

- 새 도메인은 `com.scrumble.gudocs.ocr/` 하위에 controller/service/client/parser/dto로 구성 (AGENTS.md 컨벤션)
- 업로드 이미지는 저장하지 않음 — 요청 처리 중에만 메모리에서 다루고 버림
- 이미지: `jpg`/`png`만 허용, 최대 10MB, 위반 시 `ErrorCode.INVALID_IMAGE_FILE`(400)
- CLOVA OCR 호출 실패 시 `ErrorCode.EXTERNAL_API_ERROR`(502)
- OCR은 성공했지만 필드를 못 찾은 경우는 에러가 아님 — 해당 필드만 `null`로 200 응답
- 응답 DTO(`OcrSubscriptionResult`)는 전 필드 nullable
- `serviceName`/`category`는 프론트 `CATEGORY_SERVICES` 목록과 정확히 같은 한글 canonical name으로 매칭 성공 시 채움 (로고 매칭을 위해 원문 대신 canonical name 반환)
- `firstBillingDate`는 연도 없이 추출되면 현재 연도로 채움(다음 결제일 계산은 day/month만 사용하므로 무해)
- 세션 인증 필요, `@CurrentUserId Long userId`로 주입 (기존 `subscriptions`/`notification` 컨트롤러와 동일 패턴)
- secrets(`CLOVA_OCR_SECRET_KEY`)는 코드에 하드코딩 금지, 환경변수로만 주입
- `ClovaOcrClient`는 interface로 분리해 테스트에서 mock 처리, 실제 CLOVA HTTP 호출 자체는 테스트 대상에서 제외
- 새 API이므로 테스트 클래스 필수 작성

---

### Task 1: ErrorCode 추가 + 업로드 용량 설정 + CLOVA 환경변수 설정

이후 태스크들이 의존하는 전역 설정을 먼저 준비한다. 새로운 로직은 없으므로 별도 단위 테스트 대신 기존 전체 테스트가 깨지지 않는지로 검증한다.

**Files:**
- Modify: `src/main/java/com/scrumble/gudocs/global/exception/ErrorCode.java`
- Modify: `src/main/java/com/scrumble/gudocs/global/exception/GlobalExceptionHandler.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`
- Modify: `deploy/env.example`

**Interfaces:**
- Produces: `ErrorCode.INVALID_IMAGE_FILE` (400), `ErrorCode.EXTERNAL_API_ERROR` (502) — Task 3, 5, 6에서 사용
- Produces: `spring.servlet.multipart.max-file-size=10MB` — Spring이 10MB 초과 파일을 컨트롤러 진입 전에 걸러줌
- Produces: `app.ocr.clova.invoke-url`, `app.ocr.clova.secret-key` 프로퍼티 — Task 5의 `ClovaOcrClientImpl`이 `@Value`로 주입받음

- [ ] **Step 1: `ErrorCode`에 두 항목 추가**

`src/main/java/com/scrumble/gudocs/global/exception/ErrorCode.java`의 enum 상수 목록 마지막에 추가 (기존 마지막 항목 `SAME_AS_OLD_PASSWORD(...)`  뒤 세미콜론 앞에 콤마로 연결):

```java
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다. (jpg, png, 최대 10MB)"),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "OCR 처리 중 오류가 발생했습니다.");
```

- [ ] **Step 2: `GlobalExceptionHandler`에 업로드 용량 초과 핸들러 추가**

`src/main/java/com/scrumble/gudocs/global/exception/GlobalExceptionHandler.java` 상단 import에 추가:

```java
import org.springframework.web.multipart.MaxUploadSizeExceededException;
```

클래스 마지막 메서드 뒤에 추가:

```java
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(ErrorCode.INVALID_IMAGE_FILE.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_IMAGE_FILE.getMessage()));
    }
```

- [ ] **Step 3: 메인 `application.yaml`에 multipart 설정과 CLOVA 환경변수 추가**

`src/main/resources/application.yaml`의 `spring:` 블록 안, `datasource:` 항목 위(또는 아래, 같은 레벨)에 추가:

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

`app:` 블록 마지막(`oauth:` 항목 뒤)에 추가:

```yaml
  ocr:
    clova:
      invoke-url: ${CLOVA_OCR_INVOKE_URL:}
      secret-key: ${CLOVA_OCR_SECRET_KEY:}
```

- [ ] **Step 4: 테스트 `application.yaml`에 동일 키를 리터럴 값으로 추가**

`src/test/resources/application.yaml`은 메인 yaml을 완전히 대체하므로 같은 키를 여기에도 추가해야 컨텍스트가 뜬다. `spring:` 블록에 추가:

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

`app:` 블록 마지막에 추가:

```yaml
  ocr:
    clova:
      invoke-url: http://localhost/test-clova-ocr
      secret-key: test-secret-key
```

- [ ] **Step 5: `deploy/env.example`에 CLOVA 환경변수 안내 추가**

파일 끝에 추가:

```
# CLOVA OCR (Naver Cloud Platform General OCR)
CLOVA_OCR_INVOKE_URL=CHANGE_ME
CLOVA_OCR_SECRET_KEY=CHANGE_ME
```

- [ ] **Step 6: 기존 전체 테스트가 여전히 통과하는지 확인**

Run: `./gradlew test`
Expected: 기존 테스트 전부 PASS (설정 변경으로 인한 컨텍스트 로딩 실패가 없어야 함)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/scrumble/gudocs/global/exception/ErrorCode.java \
        src/main/java/com/scrumble/gudocs/global/exception/GlobalExceptionHandler.java \
        src/main/resources/application.yaml \
        src/test/resources/application.yaml \
        deploy/env.example
git commit -m "feat: OCR 기능을 위한 ErrorCode, 업로드 용량, CLOVA 환경변수 설정 추가"
```

---

### Task 2: KnownServiceRegistry (서비스명 → 카테고리 매핑)

프론트 `CATEGORY_SERVICES`와 정확히 같은 한글 서비스명을 canonical name으로 하드코딩한 정적 매핑 테이블. 순수 로직이므로 먼저 단위 테스트로 만든다.

**Files:**
- Create: `src/main/java/com/scrumble/gudocs/ocr/parser/KnownServiceRegistry.java`
- Test: `src/test/java/com/scrumble/gudocs/ocr/parser/KnownServiceRegistryTest.java`

**Interfaces:**
- Produces: `KnownServiceRegistry.KnownService` record — `canonicalName(): String`, `category(): SubscriptionCategory`, `aliases(): List<String>`
- Produces: `KnownServiceRegistry.match(String text): Optional<KnownService>` — Task 3의 `SubscriptionTextParser`가 사용

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/scrumble/gudocs/ocr/parser/KnownServiceRegistryTest.java`:

```java
package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KnownServiceRegistryTest {

    @Test
    void canonical_name으로_매칭된다() {
        Optional<KnownServiceRegistry.KnownService> result =
                KnownServiceRegistry.match("[결제 알림] 넷플릭스 17,000원 결제되었습니다.");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("넷플릭스");
        assertThat(result.get().category()).isEqualTo(SubscriptionCategory.OTT);
    }

    @Test
    void 영문_alias로도_매칭되고_canonical_name을_반환한다() {
        Optional<KnownServiceRegistry.KnownService> result =
                KnownServiceRegistry.match("Netflix Payment Receipt");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("넷플릭스");
    }

    @Test
    void 공백_대소문자를_무시하고_매칭한다() {
        Optional<KnownServiceRegistry.KnownService> result =
                KnownServiceRegistry.match("CHATGPT PLUS 결제 안내");

        assertThat(result).isPresent();
        assertThat(result.get().canonicalName()).isEqualTo("ChatGPT");
        assertThat(result.get().category()).isEqualTo(SubscriptionCategory.AI);
    }

    @Test
    void 매칭되는_서비스가_없으면_빈값을_반환한다() {
        assertThat(KnownServiceRegistry.match("알 수 없는 서비스 결제 안내")).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*KnownServiceRegistryTest"`
Expected: FAIL (컴파일 에러 — `KnownServiceRegistry` 클래스 없음)

- [ ] **Step 3: `KnownServiceRegistry` 구현**

`src/main/java/com/scrumble/gudocs/ocr/parser/KnownServiceRegistry.java`:

```java
package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory.*;

/**
 * 프론트 CATEGORY_SERVICES와 정확히 같은 한글 서비스명을 canonical name으로 쓴다.
 * "디지니플러스"는 프론트 표기의 오타를 그대로 반영한 것이다(로고 매칭이 이름 문자열 기준이므로 임의 수정 금지).
 */
public final class KnownServiceRegistry {

    public record KnownService(String canonicalName, SubscriptionCategory category, List<String> aliases) {
    }

    private static final List<KnownService> SERVICES = List.of(
            new KnownService("유튜브 프리미엄", OTT, List.of("youtube premium", "유튜브프리미엄")),
            new KnownService("넷플릭스", OTT, List.of("netflix")),
            new KnownService("디지니플러스", OTT, List.of("disney+", "disney plus", "디즈니플러스")),
            new KnownService("티빙", OTT, List.of("tving")),
            new KnownService("쿠팡플레이", OTT, List.of("coupang play")),
            new KnownService("왓챠", OTT, List.of("watcha")),
            new KnownService("웨이브", OTT, List.of("wavve")),
            new KnownService("아마존프라임비디오", OTT, List.of("amazon prime video", "prime video")),
            new KnownService("애플TV", OTT, List.of("apple tv", "appletv")),

            new KnownService("FLO", MUSIC, List.of("플로")),
            new KnownService("유튜브뮤직", MUSIC, List.of("youtube music")),
            new KnownService("스포티파이", MUSIC, List.of("spotify")),
            new KnownService("멜론", MUSIC, List.of("melon")),
            new KnownService("애플뮤직", MUSIC, List.of("apple music")),

            new KnownService("iCloud", CLOUD, List.of("아이클라우드")),
            new KnownService("Google Drive", CLOUD, List.of("구글드라이브", "구글 드라이브")),
            new KnownService("Dropbox", CLOUD, List.of("드롭박스")),
            new KnownService("네이버 클라우드", CLOUD, List.of("naver cloud")),
            new KnownService("OneDrive", CLOUD, List.of("원드라이브")),

            new KnownService("Notion", PRODUCTIVITY, List.of("노션")),
            new KnownService("Microsoft 365", PRODUCTIVITY, List.of("ms365", "office 365")),
            new KnownService("Slack", PRODUCTIVITY, List.of("슬랙")),
            new KnownService("Google Workspace", PRODUCTIVITY, List.of("구글 워크스페이스")),

            new KnownService("ChatGPT", AI, List.of("chatgpt plus", "챗지피티", "openai")),
            new KnownService("Claude", AI, List.of("클로드", "anthropic")),
            new KnownService("Perplexity", AI, List.of("퍼플렉시티")),
            new KnownService("Gemini", AI, List.of("제미나이")),

            new KnownService("NYT", NEWS, List.of("new york times", "뉴욕타임스")),
            new KnownService("Medium", NEWS, List.of("미디엄")),
            new KnownService("퍼블리", NEWS, List.of("publy")),
            new KnownService("롱블랙", NEWS, List.of("long black")),

            new KnownService("인프런", EDUCATION, List.of("inflearn")),
            new KnownService("Udemy", EDUCATION, List.of("유데미")),
            new KnownService("Coursera", EDUCATION, List.of("코세라")),
            new KnownService("클래스101", EDUCATION, List.of("class101")),

            new KnownService("Xbox Game Pass", GAME, List.of("엑스박스 게임패스", "game pass")),
            new KnownService("PS Plus", GAME, List.of("playstation plus", "플레이스테이션 플러스")),
            new KnownService("Nintendo Switch Online", GAME, List.of("닌텐도 스위치 온라인")),

            new KnownService("쿠팡 와우", SHOPPING, List.of("coupang wow", "쿠팡와우")),
            new KnownService("네이버플러스", SHOPPING, List.of("naver plus", "네이버 플러스")),
            new KnownService("SSG.COM 유니버스클럽", SHOPPING, List.of("ssg 유니버스클럽")),

            new KnownService("Figma", DESIGN, List.of("피그마")),
            new KnownService("Adobe CC", DESIGN, List.of("adobe creative cloud", "어도비")),
            new KnownService("Canva", DESIGN, List.of("캔바"))
    );

    private KnownServiceRegistry() {
    }

    public static Optional<KnownService> match(String text) {
        String normalized = normalize(text);
        return SERVICES.stream()
                .filter(service -> matches(service, normalized))
                .findFirst();
    }

    private static boolean matches(KnownService service, String normalizedText) {
        if (normalizedText.contains(normalize(service.canonicalName()))) {
            return true;
        }
        return service.aliases().stream().anyMatch(alias -> normalizedText.contains(normalize(alias)));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*KnownServiceRegistryTest"`
Expected: PASS (4개 테스트 모두 통과)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/scrumble/gudocs/ocr/parser/KnownServiceRegistry.java \
        src/test/java/com/scrumble/gudocs/ocr/parser/KnownServiceRegistryTest.java
git commit -m "feat: OCR 서비스명-카테고리 매핑 테이블(KnownServiceRegistry) 추가"
```

---

### Task 3: OcrSubscriptionResult DTO + SubscriptionTextParser

OCR 텍스트에서 구독 필드를 뽑아내는 순수 파싱 로직. `KnownServiceRegistry`(Task 2)를 사용한다.

**Files:**
- Create: `src/main/java/com/scrumble/gudocs/ocr/dto/response/OcrSubscriptionResult.java`
- Create: `src/main/java/com/scrumble/gudocs/ocr/parser/SubscriptionTextParser.java`
- Test: `src/test/java/com/scrumble/gudocs/ocr/parser/SubscriptionTextParserTest.java`

**Interfaces:**
- Consumes: `KnownServiceRegistry.match(String): Optional<KnownServiceRegistry.KnownService>` (Task 2)
- Produces: `OcrSubscriptionResult(String serviceName, SubscriptionCategory category, Long price, BillingCycle billingCycle, LocalDate firstBillingDate, PaymentMethod paymentMethod)` — Task 4(client), Task 6(service), Task 7(controller)에서 사용
- Produces: `SubscriptionTextParser.parse(String ocrText, LocalDate today): OcrSubscriptionResult` — Task 6의 `OcrService`가 사용

- [ ] **Step 1: `OcrSubscriptionResult` DTO 작성**

`src/main/java/com/scrumble/gudocs/ocr/dto/response/OcrSubscriptionResult.java`:

```java
package com.scrumble.gudocs.ocr.dto.response;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record OcrSubscriptionResult(
        @Schema(description = "인식된 서비스명(매칭 실패 시 OCR 원문 best-effort 또는 null)", example = "넷플릭스")
        String serviceName,

        @Schema(description = "카테고리(서비스명 매칭 성공 시에만 채워짐)", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "결제 금액(원)", example = "17000")
        Long price,

        @Schema(description = "결제 주기(기본값 MONTHLY)", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "최초 결제일(연도 정보가 없으면 현재 연도로 추정)", example = "2026-07-15")
        LocalDate firstBillingDate,

        @Schema(description = "결제 수단", example = "CARD")
        PaymentMethod paymentMethod
) {
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/java/com/scrumble/gudocs/ocr/parser/SubscriptionTextParserTest.java`:

```java
package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 25);

    @Test
    void 결제_알림_캡처_텍스트를_파싱한다() {
        String text = "[카드승인]\n홍길동님 카드\n넷플릭스\n17,000원 결제\n2026.07.15";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.serviceName()).isEqualTo("넷플릭스");
        assertThat(result.category()).isEqualTo(SubscriptionCategory.OTT);
        assertThat(result.price()).isEqualTo(17000L);
        assertThat(result.firstBillingDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(result.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }

    @Test
    void 영수증_텍스트에서_연도_없는_날짜는_현재_연도로_채운다() {
        String text = "스포티파이 프리미엄\n결제일: 7월 20일\n금액 11,900원\n간편결제";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.serviceName()).isEqualTo("스포티파이");
        assertThat(result.firstBillingDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.SIMPLE_PAY);
    }

    @Test
    void 연도_포함_날짜와_연_구독_문구가_있으면_YEARLY로_판단한다() {
        String text = "Adobe CC 연간 구독\n2026-03-01 결제\n120,000원 계좌이체";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void 매칭되는_서비스가_없으면_원문_첫줄을_best_effort로_반환하고_카테고리는_null이다() {
        String text = "이상한서비스 정기결제\n5,000원";

        OcrSubscriptionResult result = SubscriptionTextParser.parse(text, TODAY);

        assertThat(result.serviceName()).isEqualTo("이상한서비스 정기결제");
        assertThat(result.category()).isNull();
    }

    @Test
    void 아무것도_인식하지_못하면_전_필드가_null이거나_기본값이다() {
        OcrSubscriptionResult result = SubscriptionTextParser.parse("", TODAY);

        assertThat(result.serviceName()).isNull();
        assertThat(result.category()).isNull();
        assertThat(result.price()).isNull();
        assertThat(result.firstBillingDate()).isNull();
        assertThat(result.paymentMethod()).isNull();
        assertThat(result.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }
}
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*SubscriptionTextParserTest"`
Expected: FAIL (컴파일 에러 — `SubscriptionTextParser` 클래스 없음)

- [ ] **Step 4: `SubscriptionTextParser` 구현**

`src/main/java/com/scrumble/gudocs/ocr/parser/SubscriptionTextParser.java`:

```java
package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLOVA OCR 텍스트에서 구독 등록 필드를 best-effort로 추출한다.
 * 정규식 기반 휴리스틱이라 완벽하지 않다 — 실패한 필드는 null로 두고 사용자가 직접 채운다.
 */
public final class SubscriptionTextParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("([0-9][0-9,]*)\\s*원");
    private static final Pattern DATE_WITH_YEAR_PATTERN =
            Pattern.compile("(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*일?");
    private static final Pattern DATE_WITHOUT_YEAR_PATTERN =
            Pattern.compile("(\\d{1,2})\\s*[./월]\\s*(\\d{1,2})\\s*일?");
    private static final Pattern YEARLY_KEYWORD_PATTERN = Pattern.compile("연\\s*(결제|구독)|연간\\s*구독");

    private SubscriptionTextParser() {
    }

    public static OcrSubscriptionResult parse(String ocrText, LocalDate today) {
        String text = ocrText == null ? "" : ocrText;

        var matched = KnownServiceRegistry.match(text);
        String serviceName = matched
                .map(service -> service.canonicalName())
                .orElseGet(() -> guessServiceName(text));
        SubscriptionCategory category = matched.map(service -> service.category()).orElse(null);

        Long price = parsePrice(text);
        LocalDate firstBillingDate = parseDate(text, today);
        BillingCycle billingCycle = parseBillingCycle(text);
        PaymentMethod paymentMethod = parsePaymentMethod(text);

        return new OcrSubscriptionResult(serviceName, category, price, billingCycle, firstBillingDate, paymentMethod);
    }

    private static String guessServiceName(String text) {
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static Long parsePrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1).replace(",", ""));
    }

    private static LocalDate parseDate(String text, LocalDate today) {
        Matcher withYear = DATE_WITH_YEAR_PATTERN.matcher(text);
        if (withYear.find()) {
            return toLocalDate(Integer.parseInt(withYear.group(1)),
                    Integer.parseInt(withYear.group(2)), Integer.parseInt(withYear.group(3)));
        }
        Matcher withoutYear = DATE_WITHOUT_YEAR_PATTERN.matcher(text);
        if (withoutYear.find()) {
            return toLocalDate(today.getYear(),
                    Integer.parseInt(withoutYear.group(1)), Integer.parseInt(withoutYear.group(2)));
        }
        return null;
    }

    private static LocalDate toLocalDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static BillingCycle parseBillingCycle(String text) {
        boolean hasYearInDate = DATE_WITH_YEAR_PATTERN.matcher(text).find();
        boolean hasYearlyKeyword = YEARLY_KEYWORD_PATTERN.matcher(text).find();
        return (hasYearInDate && hasYearlyKeyword) ? BillingCycle.YEARLY : BillingCycle.MONTHLY;
    }

    private static PaymentMethod parsePaymentMethod(String text) {
        if (text.contains("간편결제")) return PaymentMethod.SIMPLE_PAY;
        if (text.contains("계좌이체")) return PaymentMethod.BANK_TRANSFER;
        if (text.contains("카드")) return PaymentMethod.CARD;
        return null;
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*SubscriptionTextParserTest"`
Expected: PASS (5개 테스트 모두 통과)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/scrumble/gudocs/ocr/dto/response/OcrSubscriptionResult.java \
        src/main/java/com/scrumble/gudocs/ocr/parser/SubscriptionTextParser.java \
        src/test/java/com/scrumble/gudocs/ocr/parser/SubscriptionTextParserTest.java
git commit -m "feat: OCR 텍스트에서 구독 필드를 추출하는 SubscriptionTextParser 추가"
```

---

### Task 4: ClovaOcrClient (외부 API 연동)

CLOVA OCR General API 호출을 담당하는 interface + 구현체. 실제 HTTP 호출은 테스트하지 않고, 응답 JSON → 평문 텍스트 변환 로직만 순수 함수로 분리해 테스트한다.

**Files:**
- Create: `src/main/java/com/scrumble/gudocs/ocr/client/ClovaOcrClient.java`
- Create: `src/main/java/com/scrumble/gudocs/ocr/client/ClovaOcrClientImpl.java`
- Test: `src/test/java/com/scrumble/gudocs/ocr/client/ClovaOcrClientImplTest.java`

**Interfaces:**
- Produces: `ClovaOcrClient.extractText(byte[] imageBytes, String imageFormat): String` — Task 6의 `OcrService`가 사용
- Consumes: `ErrorCode.EXTERNAL_API_ERROR` (Task 1)

- [ ] **Step 1: `ClovaOcrClient` interface 작성**

`src/main/java/com/scrumble/gudocs/ocr/client/ClovaOcrClient.java`:

```java
package com.scrumble.gudocs.ocr.client;

public interface ClovaOcrClient {

    /**
     * @param imageBytes 이미지 바이트
     * @param imageFormat "jpg" 또는 "png"
     * @return OCR로 인식된 평문 텍스트(필드를 줄바꿈/공백으로 이어붙인 것)
     */
    String extractText(byte[] imageBytes, String imageFormat);
}
```

- [ ] **Step 2: 실패하는 테스트 작성 (응답 파싱 로직)**

`src/test/java/com/scrumble/gudocs/ocr/client/ClovaOcrClientImplTest.java`:

```java
package com.scrumble.gudocs.ocr.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClovaOcrClientImplTest {

    @Test
    void 필드를_lineBreak_기준으로_이어붙여_평문_텍스트로_만든다() {
        ClovaOcrResponse.Field field1 = new ClovaOcrResponse.Field("넷플릭스", true);
        ClovaOcrResponse.Field field2 = new ClovaOcrResponse.Field("17,000원", false);
        ClovaOcrResponse.Field field3 = new ClovaOcrResponse.Field("결제완료", true);
        ClovaOcrResponse.Image image = new ClovaOcrResponse.Image("SUCCESS", List.of(field1, field2, field3));
        ClovaOcrResponse response = new ClovaOcrResponse(List.of(image));

        String text = ClovaOcrClientImpl.toPlainText(response);

        assertThat(text).isEqualTo("넷플릭스\n17,000원 결제완료\n");
    }

    @Test
    void images가_비어있으면_빈_문자열을_반환한다() {
        ClovaOcrResponse response = new ClovaOcrResponse(List.of());

        assertThat(ClovaOcrClientImpl.toPlainText(response)).isEmpty();
    }
}
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*ClovaOcrClientImplTest"`
Expected: FAIL (컴파일 에러 — `ClovaOcrResponse`, `ClovaOcrClientImpl` 없음)

- [ ] **Step 4: `ClovaOcrResponse`/`ClovaOcrRequest` 및 `ClovaOcrClientImpl` 구현**

`src/main/java/com/scrumble/gudocs/ocr/client/ClovaOcrResponse.java`:

```java
package com.scrumble.gudocs.ocr.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record ClovaOcrResponse(List<Image> images) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Image(String inferResult, List<Field> fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Field(String inferText, Boolean lineBreak) {
    }
}
```

`src/main/java/com/scrumble/gudocs/ocr/client/ClovaOcrRequest.java`:

```java
package com.scrumble.gudocs.ocr.client;

import java.util.List;

record ClovaOcrRequest(String version, String requestId, long timestamp, List<Image> images) {

    record Image(String format, String name, String data) {
    }
}
```

`src/main/java/com/scrumble/gudocs/ocr/client/ClovaOcrClientImpl.java`:

```java
package com.scrumble.gudocs.ocr.client;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class ClovaOcrClientImpl implements ClovaOcrClient {

    private final RestClient restClient = RestClient.create();

    @Value("${app.ocr.clova.invoke-url}")
    private String invokeUrl;

    @Value("${app.ocr.clova.secret-key}")
    private String secretKey;

    @Override
    public String extractText(byte[] imageBytes, String imageFormat) {
        ClovaOcrRequest request = new ClovaOcrRequest(
                "V2",
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                List.of(new ClovaOcrRequest.Image(imageFormat, "image", Base64.getEncoder().encodeToString(imageBytes)))
        );

        try {
            ClovaOcrResponse response = restClient.post()
                    .uri(invokeUrl)
                    .header("X-OCR-SECRET", secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ClovaOcrResponse.class);

            return toPlainText(response);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    static String toPlainText(ClovaOcrResponse response) {
        if (response == null || response.images() == null || response.images().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ClovaOcrResponse.Image image : response.images()) {
            if (image.fields() == null) {
                continue;
            }
            for (ClovaOcrResponse.Field field : image.fields()) {
                sb.append(field.inferText());
                sb.append(Boolean.TRUE.equals(field.lineBreak()) ? "\n" : " ");
            }
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*ClovaOcrClientImplTest"`
Expected: PASS (2개 테스트 모두 통과)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/scrumble/gudocs/ocr/client/
git add src/test/java/com/scrumble/gudocs/ocr/client/ClovaOcrClientImplTest.java
git commit -m "feat: CLOVA OCR General API 연동 클라이언트(ClovaOcrClient) 추가"
```

---

### Task 5: OcrService (검증 + 오케스트레이션)

**Files:**
- Create: `src/main/java/com/scrumble/gudocs/ocr/service/OcrService.java`
- Test: `src/test/java/com/scrumble/gudocs/ocr/service/OcrServiceTest.java`

**Interfaces:**
- Consumes: `ClovaOcrClient.extractText(byte[], String): String` (Task 4), `SubscriptionTextParser.parse(String, LocalDate): OcrSubscriptionResult` (Task 3)
- Produces: `OcrService.scanSubscription(MultipartFile image): OcrSubscriptionResult` — Task 6의 `OcrController`가 사용
- Consumes: `ErrorCode.INVALID_IMAGE_FILE` (Task 1)

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/scrumble/gudocs/ocr/service/OcrServiceTest.java`:

```java
package com.scrumble.gudocs.ocr.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.ocr.client.ClovaOcrClient;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private ClovaOcrClient clovaOcrClient;

    @InjectMocks
    private OcrService ocrService;

    @Test
    void 정상_이미지는_OCR_결과를_파싱해서_반환한다() {
        MultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(clovaOcrClient.extractText(any(byte[].class), eq("jpg"))).willReturn("넷플릭스 17,000원 2026.07.15 카드");

        OcrSubscriptionResult result = ocrService.scanSubscription(image);

        assertThat(result.serviceName()).isEqualTo("넷플릭스");
        assertThat(result.price()).isEqualTo(17000L);
    }

    @Test
    void 파일이_비어있으면_예외() {
        MultipartFile empty = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> ocrService.scanSubscription(empty))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 이미지가_아닌_파일이면_예외() {
        MultipartFile pdf = new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> ocrService.scanSubscription(pdf))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 10MB_초과_파일이면_예외() {
        MultipartFile huge = new MockMultipartFile("image", "huge.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);

        assertThatThrownBy(() -> ocrService.scanSubscription(huge))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*OcrServiceTest"`
Expected: FAIL (컴파일 에러 — `OcrService` 클래스 없음)

- [ ] **Step 3: `OcrService` 구현**

`src/main/java/com/scrumble/gudocs/ocr/service/OcrService.java`:

```java
package com.scrumble.gudocs.ocr.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.ocr.client.ClovaOcrClient;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.ocr.parser.SubscriptionTextParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcrService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final ClovaOcrClient clovaOcrClient;

    public OcrSubscriptionResult scanSubscription(MultipartFile image) {
        validate(image);
        String format = "image/png".equalsIgnoreCase(image.getContentType()) ? "png" : "jpg";
        String text = clovaOcrClient.extractText(readBytes(image), format);
        return SubscriptionTextParser.parse(text, LocalDate.now());
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*OcrServiceTest"`
Expected: PASS (4개 테스트 모두 통과)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/scrumble/gudocs/ocr/service/OcrService.java \
        src/test/java/com/scrumble/gudocs/ocr/service/OcrServiceTest.java
git commit -m "feat: 이미지 검증 및 OCR 오케스트레이션을 담당하는 OcrService 추가"
```

---

### Task 6: OcrController + OcrApi (Swagger) + 통합 테스트

**Files:**
- Create: `src/main/java/com/scrumble/gudocs/ocr/controller/OcrApi.java`
- Create: `src/main/java/com/scrumble/gudocs/ocr/controller/OcrController.java`
- Test: `src/test/java/com/scrumble/gudocs/ocr/controller/OcrControllerTest.java`

**Interfaces:**
- Consumes: `OcrService.scanSubscription(MultipartFile): OcrSubscriptionResult` (Task 5)
- Produces: `POST /api/ocr/subscriptions/scan` — 최종 엔드포인트

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/scrumble/gudocs/ocr/controller/OcrControllerTest.java`:

```java
package com.scrumble.gudocs.ocr.controller;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.ocr.client.ClovaOcrClient;
import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @MockitoBean
    private ClovaOcrClient clovaOcrClient;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, "테스터", "ocr@example.com");
    }

    @Test
    void 이미지를_업로드하면_인식된_구독_정보를_반환한다() throws Exception {
        given(clovaOcrClient.extractText(any(byte[].class), any(String.class)))
                .willReturn("넷플릭스 17,000원 2026.07.15 카드");
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(image).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceName").value("넷플릭스"))
                .andExpect(jsonPath("$.data.category").value("OTT"))
                .andExpect(jsonPath("$.data.price").value(17000));
    }

    @Test
    void 이미지가_아닌_파일이면_400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(file).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 미인증_401() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(image))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CLOVA_호출_실패시_502() throws Exception {
        given(clovaOcrClient.extractText(any(byte[].class), any(String.class)))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(image).session(session))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*OcrControllerTest"`
Expected: FAIL (컴파일 에러 — `OcrController` 없음, 라우트 없음)

- [ ] **Step 3: `OcrApi`, `OcrController` 구현**

`src/main/java/com/scrumble/gudocs/ocr/controller/OcrApi.java`:

```java
package com.scrumble.gudocs.ocr.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "OCR", description = "CLOVA OCR 기반 구독 정보 인식 API")
@SecurityRequirement(name = "cookieAuth")
public interface OcrApi {

    @Operation(summary = "구독 결제 이미지 OCR 인식",
            description = "결제 알림 캡처/영수증/구독 화면 이미지를 업로드하면 CLOVA OCR로 텍스트를 추출하고 "
                    + "구독 등록에 필요한 필드를 best-effort로 파싱해 반환합니다. 인식 실패 필드는 null입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인식 성공(일부 필드 null 가능)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미지 파일이 없거나 형식/용량이 유효하지 않음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "CLOVA OCR API 호출 실패")
    })
    ResponseEntity<ApiResponse<OcrSubscriptionResult>> scan(
            @CurrentUserId Long userId,
            @Parameter(description = "구독 결제 관련 이미지 (jpg/png, 최대 10MB)") MultipartFile image);
}
```

`src/main/java/com/scrumble/gudocs/ocr/controller/OcrController.java`:

```java
package com.scrumble.gudocs.ocr.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.ocr.service.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController implements OcrApi {

    private final OcrService ocrService;

    @Override
    @PostMapping(value = "/subscriptions/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OcrSubscriptionResult>> scan(
            @CurrentUserId Long userId, @RequestPart("image") MultipartFile image) {
        OcrSubscriptionResult result = ocrService.scanSubscription(image);
        return ResponseEntity.ok(ApiResponse.success("구독 정보 인식에 성공했습니다.", result));
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*OcrControllerTest"`
Expected: PASS (4개 테스트 모두 통과)

- [ ] **Step 5: 전체 테스트 스위트 실행 (회귀 확인)**

Run: `./gradlew test`
Expected: 전체 PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/scrumble/gudocs/ocr/controller/ \
        src/test/java/com/scrumble/gudocs/ocr/controller/OcrControllerTest.java
git commit -m "feat: POST /api/ocr/subscriptions/scan 엔드포인트 추가"
```

---

## Out of scope (스펙 문서와 동일)

- 이미지 저장/재조회 기능
- Custom/Template OCR 도메인 학습
- OCR 결과 자동 저장(항상 사용자 확인 단계를 거침)
- alias 테이블의 완전 자동 갱신(신규 서비스 추가 시 코드 수정 필요)
