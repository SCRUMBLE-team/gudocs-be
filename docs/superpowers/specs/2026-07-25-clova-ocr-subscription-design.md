# CLOVA OCR 기반 구독 등록 보조 기능 설계

날짜: 2026-07-25

## 배경 / 목적

사용자가 결제 알림 캡처, 영수증 사진, 구독 서비스 화면 캡처 등 다양한 이미지를 업로드하면
CLOVA OCR로 텍스트를 추출하고, 그 텍스트에서 구독 등록에 필요한 필드(서비스명, 카테고리,
금액, 결제 주기, 최초 결제일, 결제 수단)를 최대한 파싱해 프론트의 구독 등록 폼을 미리
채워준다. 최종 저장은 기존 `POST /api/subscriptions`를 그대로 사용하며, 이 기능은
입력을 줄여주는 보조 수단이다.

## 아키텍처

새 도메인 `com.scrumble.gudocs.ocr/`을 추가한다.

```
ocr/
  controller/OcrController.java       # POST /api/ocr/subscriptions/scan
  service/OcrService.java             # 오케스트레이션: client 호출 → parser 위임
  client/ClovaOcrClient.java          # interface (외부 API 경계, 테스트에서 mock)
  client/ClovaOcrClientImpl.java      # RestClient로 CLOVA General OCR 호출
  parser/SubscriptionTextParser.java  # OCR 텍스트 → 구독 필드 추출 (순수 함수)
  parser/KnownServiceRegistry.java    # 서비스명 → category 매핑 테이블
  dto/response/OcrSubscriptionResult.java
```

**흐름**: FE가 이미지를 `multipart/form-data`로 업로드 → `OcrController` → `OcrService`가
`ClovaOcrClient`로 OCR 텍스트 추출 → `SubscriptionTextParser`가 텍스트에서 필드를
파싱하면서 `KnownServiceRegistry`로 서비스명↔카테고리를 매핑 → `OcrSubscriptionResult`
(전 필드 nullable)를 반환 → FE가 구독 등록 폼을 프리필하고 사용자가 확인/수정 후 기존
`POST /api/subscriptions`로 최종 등록.

업로드된 이미지는 저장하지 않는다. 요청 처리 중 메모리에서만 다루고 응답 후 버린다.

## API 명세

### `POST /api/ocr/subscriptions/scan`

- 인증: 세션 쿠키 필요 (기존 `subscriptions` 컨트롤러와 동일하게 `@CurrentUserId Long userId`로 주입받음)
- Request: `multipart/form-data`, 필드명 `image`, `jpg`/`png`만 허용, 최대 10MB
- Response:

```json
{
  "success": true,
  "message": "구독 정보 인식에 성공했습니다.",
  "data": {
    "serviceName": "넷플릭스",
    "category": "OTT",
    "price": 17000,
    "billingCycle": "MONTHLY",
    "firstBillingDate": "2026-07-15",
    "paymentMethod": "CARD"
  }
}
```

모든 필드는 nullable이다. 파싱에 실패한 필드는 `null`로 내려가고, FE는 해당 필드를
사용자가 직접 입력하게 한다.

### 에러 케이스

| 상황 | 처리 |
|---|---|
| 파일 없음 / 이미지 아님 / 10MB 초과 | `400 BAD_REQUEST` |
| CLOVA OCR API 호출 실패(네트워크/인증 오류) | 새 `ErrorCode.EXTERNAL_API_ERROR` → `502` |
| OCR은 성공했지만 필드를 하나도 못 찾음 | 에러 아님. 전 필드 `null`인 채로 `200` 응답 |

## 필드 추출 규칙

`SubscriptionTextParser`가 CLOVA OCR이 반환한 전체 텍스트를 입력받아 다음을 파싱한다.

- **serviceName / category**: `KnownServiceRegistry`에 등록된 canonical name(프론트
  `CATEGORY_SERVICES`의 정확한 한글 서비스명) 또는 그 alias와 대소문자/공백 무시
  부분일치를 시도한다. 매치되면 `serviceName`은 canonical name 그대로(OCR 원문이 아님),
  `category`도 함께 채운다. 매치 안 되면 `serviceName`은 OCR 텍스트에서 뽑은 best-effort
  후보, `category`는 `null`.
- **price**: `\d[\d,]*\s*원` 패턴에서 숫자를 추출하고 콤마를 제거한다.
- **firstBillingDate**: `YYYY.MM.DD`, `YYYY-MM-DD`, `MM/DD`, `M월 D일` 등의 날짜 패턴을
  찾아 `LocalDate`로 조립한다. 연도가 텍스트에 없으면 현재 연도로 채운다(
  `NextBillingDateCalculator`는 앵커가 과거면 day/month만 사용하므로 연도 추정 오차가
  다음 결제일 계산 결과에 영향을 주지 않는다). 날짜를 전혀 못 찾으면 `null`.
- **billingCycle**: 기본값 `MONTHLY`. 날짜에 연도가 명시돼 있고 "연 결제"/"연간 구독"
  등의 문구가 함께 있으면 `YEARLY`.
- **paymentMethod**: "카드" → `CARD`, "계좌이체" → `BANK_TRANSFER`, "간편결제" →
  `SIMPLE_PAY` 키워드 매치. 매치 안 되면 `null`.

### KnownServiceRegistry

프론트 `CATEGORY_SERVICES`(현재 ~40개 서비스, `ETC` 제외)에 있는 정확한 한글 서비스명을
canonical name으로 하드코딩한 정적 테이블. 각 항목은 canonical name + alias 목록(영문
브랜드명, 흔한 표기 1~3개)을 가진다.

```java
record KnownService(String canonicalName, SubscriptionCategory category, List<String> aliases) {}

new KnownService("넷플릭스", OTT, List.of("netflix", "넷플"))
new KnownService("유튜브 프리미엄", OTT, List.of("youtube premium", "유튜브프리미엄"))
new KnownService("챗지피티", AI, List.of("chatgpt", "chatgpt plus", "openai"))
```

`ETC`는 프론트에도 고정 목록이 없으므로 매핑 대상에서 제외한다 — 매치 안 되면 그대로
`category: null`로 두고 사용자가 직접 고르게 한다.

## CLOVA OCR 연동

- Naver Cloud Platform CLOVA OCR **General** 도메인 API 사용 (커스텀 템플릿 학습 불필요)
- `ClovaOcrClient` 인터페이스로 외부 API 호출을 추상화하고, `ClovaOcrClientImpl`이
  실제 HTTP 호출(`RestClient`)을 담당한다.
- 설정: `application.yaml`에 `CLOVA_OCR_INVOKE_URL`, `CLOVA_OCR_SECRET_KEY` 환경변수를
  추가하고(기본값은 로컬 개발용 빈 값), `deploy/env.example`에도 동일하게 추가한다.
  secret은 코드/응답에 노출하지 않는다(기존 프로젝트 규칙).

## ErrorCode 추가

```
EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "OCR 처리 중 오류가 발생했습니다.")
```

기존 `ErrorCode` enum에 추가한다. 이미 동일 의미의 코드는 없다.

## 테스트 전략

- `ClovaOcrClient`를 인터페이스로 분리했으므로 `OcrService` 테스트는 이를 mock한다
  (실제 CLOVA API 호출 없이 CI에서 동작).
- `SubscriptionTextParser`, `KnownServiceRegistry`는 순수 로직이므로 다양한 샘플 OCR
  텍스트(결제 알림 캡처, 영수증, 구독 화면 캡처 각각의 대표 텍스트)를 입력으로 하는
  단위 테스트를 작성해 필드별 추출 정확도를 검증한다.
- `OcrController`는 파일 검증(빈 파일/비이미지/용량 초과) 관련 400 응답만 테스트하고,
  실제 CLOVA 연동 자체는 통합 테스트 대상에서 제외한다.

## 범위 밖 (Out of scope)

- 이미지 저장/재조회 기능
- Custom/Template OCR 도메인 학습
- OCR 결과 자동 저장(항상 사용자 확인 단계를 거침)
- alias 테이블의 완전 자동 갱신(신규 서비스 추가 시 코드 수정 필요)
