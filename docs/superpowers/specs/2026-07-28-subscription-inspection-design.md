# 구독 점검(미사용/중복 감지) 기능 설계

날짜: 2026-07-28

## 배경 / 목적

대시보드에 "구독 점검" 버튼을 추가하고, 클릭 시 팝업으로 두 종류의 해지 후보를 보여준다.

1. **미사용 구독 후보** — 오래 손대지 않은 채 계속 결제되고 있는 구독
2. **카테고리 중복 구독 후보** — 같은 카테고리에 여러 개 구독 중인 경우

사용자가 팝업에서 체크박스로 해지할 항목을 선택하면, 프론트가 각 항목의 `monthlyAmount`를
합산해 예상 절약액을 실시간으로 보여준다. 이 문서는 백엔드가 두 후보군을 계산해 내려주는
API를 정의한다.

현재 시스템에는 실제 서비스 이용(로그인, 앱 실행 등) 데이터가 없으므로, "미사용"은
실사용 여부가 아니라 **"오래 정보가 바뀌지 않은 구독"** 을 근사치로 사용한다.

## 판단 기준

- **대상**: `ACTIVE` 상태 구독만 (`PAUSED`, soft-delete된 구독은 제외 — 기존
  `DashboardService.getDashboard`의 `active` 필터링과 동일한 정책)
- **미사용 후보**: `updatedAt`이 오늘 기준 6개월 이상 지난 ACTIVE 구독
- **카테고리 중복 후보**: 카테고리별로 ACTIVE 구독을 그룹핑했을 때 그룹 크기가 2 이상인
  카테고리. 단 `ETC`는 성격이 잡다한 항목들의 묶음이라 "같은 카테고리 = 중복 서비스"라는
  전제가 성립하지 않으므로 판정 대상에서 제외한다.
- **절약액 계산은 프론트 책임**: 서버는 각 후보 구독의 `monthlyAmount`(월 환산 금액)만
  내려주고, 사용자가 체크한 항목들의 합산은 프론트에서 계산한다. 서버는 어떤 구독을
  "남길지" 결정하지 않는다.
- **저장 없음**: 점검 결과는 DB에 저장하지 않고, 요청마다 실시간으로 계산한다. "무시(dismiss)"
  상태 같은 것도 없다 — 이 범위는 명시적으로 다루지 않는다 (YAGNI).

## 아키텍처

새 도메인을 만들지 않고 기존 `dashboard` 도메인에 추가한다.

```
dashboard/
  service/DashboardService.java         # getInspection(Long userId) 메서드 추가
  dto/InspectionResponse.java
  dto/UnusedSubscriptionCandidate.java
  dto/DuplicateCategoryGroup.java
  dto/DuplicateSubscriptionItem.java
  controller/DashboardApi.java          # getInspection 시그니처 추가
  controller/DashboardController.java   # GET /api/dashboard/inspection

subscriptions/
  util/MonthlyAmountCalculator.java     # 신규: 월 환산 금액 계산 단일 소스
```

### 리팩터링: `MonthlyAmountCalculator` 추출

`DashboardService`에는 이미 월 환산 금액을 계산하는 private 메서드
(`monthlyAmount(Subscription)`, `MONTHLY`면 `price` 그대로, `YEARLY`면 `price / 12`
Long 버림)가 있다. 이번 기능도 동일 계산이 필요하므로, 프로젝트가 이미 `NextBillingDateCalculator`로
"결제일 계산 단일 소스" 원칙을 쓰고 있는 것과 같은 방식으로
`subscriptions/util/MonthlyAmountCalculator.calculate(Subscription)`로 추출해
`DashboardService`의 기존 로직과 새 `getInspection`에서 함께 쓴다.

### 흐름

`GET /api/dashboard/inspection` → `DashboardController` → `DashboardService.getInspection(userId)`
→ `SubscriptionRepository.findAllByUserOrderByCreatedAtDesc(user)`로 조회 → `ACTIVE` 필터 →

- `updatedAt <= today.minusMonths(6)` 인 것들을 `unusedCandidates`로 매핑
- `ETC`를 제외하고 `category`로 그룹핑 → 그룹 크기 2 이상인 것만 `duplicateGroups`로 매핑

## API 명세

### `GET /api/dashboard/inspection`

- 인증: 세션 쿠키 필요, `@CurrentUserId Long userId`
- Request body 없음

```json
{
  "success": true,
  "message": "구독 점검에 성공했습니다.",
  "data": {
    "unusedCandidates": [
      {
        "id": 3,
        "serviceName": "Adobe CC",
        "category": "DESIGN",
        "price": 24000,
        "billingCycle": "MONTHLY",
        "monthlyAmount": 24000,
        "updatedAt": "2026-01-10T09:00:00"
      }
    ],
    "duplicateGroups": [
      {
        "category": "OTT",
        "subscriptions": [
          { "id": 1, "serviceName": "Netflix", "price": 17000, "billingCycle": "MONTHLY", "monthlyAmount": 17000 },
          { "id": 5, "serviceName": "Watcha", "price": 12900, "billingCycle": "MONTHLY", "monthlyAmount": 12900 }
        ]
      }
    ]
  }
}
```

두 후보군 모두 해당 사항이 없으면 각각 빈 배열을 내려준다 (에러 아님).

#### Error

- `401 Unauthorized`: 로그인 세션이 없거나 만료된 경우
- `404 Not Found`: 사용자를 찾을 수 없는 경우 (`USER_NOT_FOUND`, 기존 `DashboardService.getDashboard`와 동일 정책)

## 테스트 계획

`DashboardServiceTest`에 케이스를 추가한다 (기존 `getDashboard(userId, today)` 오버로드로
기준 날짜를 주입하는 테스트 패턴을 그대로 재사용).

- `updatedAt`이 정확히 6개월 전 / 6개월에서 하루 모자란 경계값
- `PAUSED` 구독은 `unusedCandidates`, `duplicateGroups` 양쪽 모두에서 제외되는지
- 같은 카테고리에 ACTIVE 구독이 1개뿐이면 `duplicateGroups`에 안 잡히는지
- `ETC` 카테고리는 2개 이상이어도 `duplicateGroups`에서 제외되는지
- `YEARLY` 구독의 `monthlyAmount`가 `price / 12` (버림)로 정확히 계산되는지
- 후보가 하나도 없을 때 양쪽 다 빈 배열을 반환하는지

`MonthlyAmountCalculator`는 순수 함수이므로 별도 단위 테스트
(`MonthlyAmountCalculatorTest`)를 작성한다.

## 범위 밖 (다루지 않음)

- 실제 서비스 이용 여부 추적 (로그인 연동, 사용 API 등)
- 점검 결과의 서버 측 저장/무시(dismiss) 상태
- "어떤 구독을 남길지" 자동 추천 — 프론트/사용자 판단 영역
- 미사용 판단 기간(6개월)을 사용자가 조정하는 기능
