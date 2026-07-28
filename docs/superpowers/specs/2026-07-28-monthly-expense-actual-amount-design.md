# 월별 지출 - 실결제금액 필드 추가 설계

날짜: 2026-07-28

## 배경 / 목적

`GET /api/subscriptions/expenses/monthly`는 현재 YEARLY 구독을 항상 `price / 12`로 평균
환산해서 보여준다. 이 방식은 예산 계획에는 유용하지만, "이번 달에 실제로 통장에서 얼마가
나갔는지"는 알 수 없다 — 연 구독은 결제되는 특정 달에만 전액이 빠져나가고 나머지 11개월은
0원이기 때문이다.

이번 변경은 기존 "평균 기준" 필드는 그대로 두고, "이번 달에 실제로 결제된 금액" 기준의
필드를 하나 추가한다.

## 판단 기준

- **대상**: 기존 `ExpenseService.filterByMonth`가 걸러낸 "해당 월 결제 대상 구독" 리스트를
  그대로 재사용한다 (createdAt/deletedAt/pausedAt 조건 동일, 새 조건 추가 없음)
- **MONTHLY 구독**: 평균 기준과 동일하게 매달 `price` 전액
- **YEARLY 구독**: `firstBillingDate`의 월(month)이 조회 월(`YearMonth`)의 월과 같으면 `price`
  전액, 다르면 `0`. 연도는 비교하지 않는다 — 매년 같은 달에 결제된다고 가정 (기존
  `NextBillingDateCalculator.nextYearly`와 동일한 전제)
- **적용 범위**: `getMonthlyExpense` (월별 지출 요약)만. 카테고리별/추이/월별상세는 지금처럼
  평균 기준을 유지한다 (범위 밖)

## 응답 변경

`MonthlyExpenseResponse`에 필드 하나만 추가한다. 기존 8개 필드는 그대로 둔다.

```json
{
  "year": 2026, "month": 3,
  "totalAmount": 80000,
  "previousMonthAmount": 75000,
  "changeAmount": 5000,
  "changeRate": 6.67,
  "monthlySubscriptionAmount": 50000,
  "annualSubscriptionMonthlyConvertedAmount": 30000,
  "actualAmount": 130000
}
```

`actualAmount`: 해당 월에 실제로 결제된 금액 합계 (MONTHLY 전액 + 그 달이 결제월인 YEARLY 전액).
전월 대비 증감액/증감률 같은 비교 필드는 추가하지 않는다 (범위 밖 — 필요해지면 나중에 추가).

## 구현

`subscriptions/util/MonthlyAmountCalculator`(이번에 새로 추출하는 유틸)에 실결제금액 계산
메서드를 함께 둔다.

```java
public static long monthlyAmount(Subscription s) { ... }               // 기존 평균 기준
public static long actualAmount(Subscription s, YearMonth target) { ... } // 신규 실결제 기준
```

`ExpenseService.getMonthlyExpense`에서 `currentMonth` 리스트에 `actualAmount`를 합산해
`actualAmount` 필드로 응답에 채운다.

## 테스트 계획

`MonthlyAmountCalculatorTest`에 `actualAmount` 케이스 추가:

- MONTHLY 구독은 조회 월과 무관하게 항상 `price` 전액
- YEARLY 구독, 결제월과 조회월이 같은 경우 → `price` 전액
- YEARLY 구독, 결제월과 조회월이 다른 경우 → `0`
- YEARLY 구독, 조회 연도가 달라도 월만 같으면 전액 (연도 무시 확인)

`ExpenseServiceTest`에 `getMonthlyExpense` 케이스 추가:

- MONTHLY + YEARLY(결제월 일치) + YEARLY(결제월 불일치) 구독이 섞여 있을 때 `actualAmount`가
  올바르게 합산되는지
- 기존 `totalAmount` 등 평균 기준 필드는 이번 변경으로 값이 달라지지 않는지 (회귀 확인)

## 범위 밖 (다루지 않음)

- 카테고리별/추이/월별상세 API에 실결제 기준 적용
- 실결제금액의 전월 대비 증감액/증감률
- 프론트 표시 방식 (실결제/평균 토글 UI 등)
