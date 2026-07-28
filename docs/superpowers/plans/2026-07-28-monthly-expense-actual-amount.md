# 월별 지출 실결제금액(actualAmount) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/subscriptions/expenses/monthly` 응답에 "이번 달에 실제로 결제된 금액"(`actualAmount`) 필드를 추가한다. YEARLY 구독은 결제되는 특정 달에만 전액이 잡히고 나머지 달은 0원으로 계산된다.

**Architecture:** `subscriptions/util/MonthlyAmountCalculator`를 새로 만들어 `DashboardService`와 `ExpenseService`에 중복돼 있던 "월 환산 금액(평균 기준)" 계산을 단일 소스로 합치고, 같은 유틸에 "실결제 기준" 계산 메서드를 추가한다. `ExpenseService.getMonthlyExpense`가 이 유틸을 호출해 `actualAmount`를 채운다.

**Tech Stack:** Spring Boot 3.5 / Java 21, JUnit 5 + AssertJ + Mockito(MockitoExtension), `@SpringBootTest` + `MockMvc` 통합 테스트(H2)

## Global Constraints

- 새 의존성 추가 금지 — 기존 스택(JUnit5/AssertJ/Mockito, Spring Web/Data JPA)만 사용
- 계산 로직은 단일 소스로 관리 (프로젝트 기존 `NextBillingDateCalculator` 패턴을 따름)
- 새 API 필드 추가 시 테스트 필수
- 리팩터링(DashboardService, ExpenseService)은 동작 변경이 없어야 하며, 기존 테스트가 수정 없이 그대로 통과해야 함
- 커밋은 태스크 단위로 자주

---

## 참고: 현재 중복 코드

`DashboardService.java:62-64`와 `ExpenseService.java:167-169`에 아래와 동일한 private 메서드가 각각 존재한다.

```java
private long monthlyAmount(Subscription s) {
    return s.getBillingCycle() == BillingCycle.MONTHLY ? s.getPrice() : s.getPrice() / 12;
}
```

### Task 1: `MonthlyAmountCalculator.monthlyAmount` 추출

**Files:**
- Create: `src/main/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculator.java`
- Test: `src/test/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculatorTest.java`

**Interfaces:**
- Produces: `MonthlyAmountCalculator.monthlyAmount(Subscription subscription): long` — `MONTHLY`면 `price` 그대로, `YEARLY`면 `price / 12`(Long 정수 나눗셈, 버림)

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculatorTest.java` 새로 작성:

```java
package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.common.fixture.UserFixture;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.users.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyAmountCalculatorTest {

    private final User user = UserFixture.create();

    private Subscription monthly(long price, int billingDay) {
        return Subscription.builder()
                .user(user)
                .serviceName("Netflix")
                .category(SubscriptionCategory.OTT)
                .price(price)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.of(2025, 1, billingDay))
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    private Subscription yearly(long price, int billingMonth, int billingDay) {
        return Subscription.builder()
                .user(user)
                .serviceName("Adobe")
                .category(SubscriptionCategory.DESIGN)
                .price(price)
                .billingCycle(BillingCycle.YEARLY)
                .firstBillingDate(LocalDate.of(2025, billingMonth, billingDay))
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    @Test
    void MONTHLY_구독은_price_전액() {
        Subscription s = monthly(17000L, 15);

        assertThat(MonthlyAmountCalculator.monthlyAmount(s)).isEqualTo(17000L);
    }

    @Test
    void YEARLY_구독은_price를_12로_나눈_값_버림() {
        Subscription s = yearly(100000L, 3, 1);

        assertThat(MonthlyAmountCalculator.monthlyAmount(s)).isEqualTo(8333L);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*MonthlyAmountCalculatorTest"`
Expected: FAIL — `MonthlyAmountCalculator` 클래스가 없어서 컴파일 에러

- [ ] **Step 3: 최소 구현 작성**

```java
package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;

public final class MonthlyAmountCalculator {

    private MonthlyAmountCalculator() {
    }

    public static long monthlyAmount(Subscription subscription) {
        return subscription.getBillingCycle() == BillingCycle.MONTHLY
                ? subscription.getPrice()
                : subscription.getPrice() / 12;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*MonthlyAmountCalculatorTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculator.java src/test/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculatorTest.java
git commit -m "feat: MonthlyAmountCalculator 유틸 추가 (평균 기준 월 환산 금액)"
```

---

### Task 2: `MonthlyAmountCalculator.actualAmount` 추가

**Files:**
- Modify: `src/main/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculator.java`
- Test: `src/test/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculatorTest.java`

**Interfaces:**
- Consumes: Task 1의 `MonthlyAmountCalculator` 클래스 (같은 파일에 메서드 추가)
- Produces: `MonthlyAmountCalculator.actualAmount(Subscription subscription, YearMonth target): long` — `MONTHLY`는 항상 `price` 전액. `YEARLY`는 `subscription.getFirstBillingDate().getMonthValue() == target.getMonthValue()`일 때만 `price` 전액, 아니면 `0`. 연도는 비교하지 않는다.

- [ ] **Step 1: 실패하는 테스트 추가**

`MonthlyAmountCalculatorTest.java`에 아래 4개 테스트 메서드를 추가 (기존 2개 테스트 아래):

```java
    @Test
    void MONTHLY_구독은_조회월과_무관하게_항상_price_전액() {
        Subscription s = monthly(17000L, 15);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 1)))
                .isEqualTo(17000L);
        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 7)))
                .isEqualTo(17000L);
    }

    @Test
    void YEARLY_구독은_결제월과_조회월이_같으면_price_전액() {
        Subscription s = yearly(120000L, 3, 1);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 3)))
                .isEqualTo(120000L);
    }

    @Test
    void YEARLY_구독은_결제월과_조회월이_다르면_0원() {
        Subscription s = yearly(120000L, 3, 1);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2026, 4)))
                .isEqualTo(0L);
    }

    @Test
    void YEARLY_구독은_연도가_달라도_월만_같으면_price_전액() {
        Subscription s = yearly(120000L, 3, 1);

        assertThat(MonthlyAmountCalculator.actualAmount(s, YearMonth.of(2030, 3)))
                .isEqualTo(120000L);
    }
```

파일 상단 import에 `import java.time.YearMonth;` 추가.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*MonthlyAmountCalculatorTest"`
Expected: FAIL — `actualAmount` 메서드가 없어서 컴파일 에러

- [ ] **Step 3: 구현 추가**

`MonthlyAmountCalculator.java`에 메서드 추가 (import에 `java.time.YearMonth` 추가):

```java
    public static long actualAmount(Subscription subscription, YearMonth target) {
        if (subscription.getBillingCycle() == BillingCycle.MONTHLY) {
            return subscription.getPrice();
        }
        boolean billedThisMonth = subscription.getFirstBillingDate().getMonthValue() == target.getMonthValue();
        return billedThisMonth ? subscription.getPrice() : 0L;
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*MonthlyAmountCalculatorTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculator.java src/test/java/com/scrumble/gudocs/subscriptions/util/MonthlyAmountCalculatorTest.java
git commit -m "feat: MonthlyAmountCalculator에 실결제 기준 계산(actualAmount) 추가"
```

---

### Task 3: `DashboardService`가 `MonthlyAmountCalculator` 사용하도록 리팩터링

**Files:**
- Modify: `src/main/java/com/scrumble/gudocs/dashboard/service/DashboardService.java`

**Interfaces:**
- Consumes: Task 1의 `MonthlyAmountCalculator.monthlyAmount(Subscription): long`
- Produces: 없음 (동작 변경 없는 순수 리팩터링)

이 태스크는 동작을 바꾸지 않는다 — 기존 `DashboardServiceTest`가 수정 없이 그대로 통과해야 검증 완료다.

- [ ] **Step 1: 리팩터링 전 기준선 확인**

Run: `./gradlew test --tests "*DashboardServiceTest"`
Expected: PASS (기존 테스트 전부 — 리팩터링 전 상태 확인용)

- [ ] **Step 2: private `monthlyAmount` 제거하고 유틸로 교체**

`DashboardService.java`에서 다음을 변경:

1. import 추가: `import com.scrumble.gudocs.subscriptions.util.MonthlyAmountCalculator;`
2. import 제거: `import com.scrumble.gudocs.subscriptions.entity.BillingCycle;` (더 이상 이 파일에서 쓰지 않음)
3. `calculateMonthlyTotal` 메서드를 아래로 교체:

```java
    private long calculateMonthlyTotal(List<Subscription> subscriptions) {
        return subscriptions.stream().mapToLong(MonthlyAmountCalculator::monthlyAmount).sum();
    }
```

4. `calculateCategorySummaries` 안의 `Collectors.summingLong(this::monthlyAmount)`를
   `Collectors.summingLong(MonthlyAmountCalculator::monthlyAmount)`로 교체
5. private `monthlyAmount(Subscription s)` 메서드 전체 삭제

- [ ] **Step 3: 리팩터링 후 회귀 확인**

Run: `./gradlew test --tests "*DashboardServiceTest"`
Expected: PASS — Step 1과 동일한 테스트가 동일하게 전부 통과 (동작 변경 없음 확인)

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/scrumble/gudocs/dashboard/service/DashboardService.java
git commit -m "refactor: DashboardService가 MonthlyAmountCalculator 공유하도록 정리"
```

---

### Task 4: `ExpenseService`가 `MonthlyAmountCalculator` 사용하도록 리팩터링

**Files:**
- Modify: `src/main/java/com/scrumble/gudocs/expense/service/ExpenseService.java`

**Interfaces:**
- Consumes: Task 1의 `MonthlyAmountCalculator.monthlyAmount(Subscription): long`
- Produces: 없음 (동작 변경 없는 순수 리팩터링)

이 태스크도 동작을 바꾸지 않는다 — 기존 `ExpenseControllerTest`가 수정 없이 그대로 통과해야 검증 완료다.

- [ ] **Step 1: 리팩터링 전 기준선 확인**

Run: `./gradlew test --tests "*ExpenseControllerTest"`
Expected: PASS (기존 테스트 전부)

- [ ] **Step 2: private `monthlyAmount` 제거하고 유틸로 교체**

`ExpenseService.java`에서 다음을 변경:

1. import 추가: `import com.scrumble.gudocs.subscriptions.util.MonthlyAmountCalculator;`
2. `sumMonthlyAmount` 메서드를 아래로 교체:

```java
    private long sumMonthlyAmount(List<Subscription> subscriptions) {
        return subscriptions.stream().mapToLong(MonthlyAmountCalculator::monthlyAmount).sum();
    }
```

3. `getMonthlyExpenseDetail` 안의 `monthlyAmount(s)` 호출 2곳(정렬용 `Comparator.comparingLong`, `SubscriptionExpenseDetail` 생성자 인자)을
   `MonthlyAmountCalculator.monthlyAmount(s)`로 교체
4. private `monthlyAmount(Subscription s)` 메서드 전체 삭제 (`BillingCycle` import는 `filterByCycle`, `BillingCycle.MONTHLY`/`BillingCycle.YEARLY` 참조에 계속 쓰이므로 유지)

- [ ] **Step 3: 리팩터링 후 회귀 확인**

Run: `./gradlew test --tests "*ExpenseControllerTest"`
Expected: PASS — Step 1과 동일한 테스트가 동일하게 전부 통과

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/scrumble/gudocs/expense/service/ExpenseService.java
git commit -m "refactor: ExpenseService가 MonthlyAmountCalculator 공유하도록 정리"
```

---

### Task 5: `actualAmount` 필드를 월별 지출 응답에 추가

**Files:**
- Modify: `src/main/java/com/scrumble/gudocs/expense/dto/response/MonthlyExpenseResponse.java`
- Modify: `src/main/java/com/scrumble/gudocs/expense/service/ExpenseService.java`
- Test: `src/test/java/com/scrumble/gudocs/expense/controller/ExpenseControllerTest.java`

**Interfaces:**
- Consumes: Task 2의 `MonthlyAmountCalculator.actualAmount(Subscription, YearMonth): long`
- Produces: `MonthlyExpenseResponse.actualAmount(): long` — `GET /api/subscriptions/expenses/monthly` 응답의 새 필드

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`ExpenseControllerTest.java`에 아래 3개 테스트를 기존 `월별_지출_분석_전월_0원_changeRate_0()` 테스트 아래에 추가:

```java
    @Test
    void 월별_지출_분석_실결제금액_MONTHLY만_있으면_totalAmount와_동일() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(27000));
    }

    @Test
    void 월별_지출_분석_실결제금액_YEARLY_결제월과_같으면_전액() throws Exception {
        YearMonth now = 현재월();
        구독_등록("Adobe", SubscriptionCategory.DESIGN, 120000L, BillingCycle.YEARLY, 1, now.getMonthValue());
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);

        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(137000));
    }

    @Test
    void 월별_지출_분석_실결제금액_YEARLY_결제월과_다르면_0원() throws Exception {
        YearMonth now = 현재월();
        int otherMonth = now.getMonthValue() % 12 + 1;
        구독_등록("Adobe", SubscriptionCategory.DESIGN, 120000L, BillingCycle.YEARLY, 1, otherMonth);
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);

        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(17000));
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*ExpenseControllerTest"`
Expected: FAIL — 새 3개 테스트가 `$.data.actualAmount` 경로를 못 찾아서 실패 (필드가 아직 없음)

- [ ] **Step 3: `MonthlyExpenseResponse`에 필드 추가**

`MonthlyExpenseResponse.java`의 마지막 필드(`annualSubscriptionMonthlyConvertedAmount`) 뒤에 콤마를 추가하고 새 필드를 붙인다:

```java
public record MonthlyExpenseResponse(
        @Schema(description = "조회 연도", example = "2026")
        int year,

        @Schema(description = "조회 월", example = "7")
        int month,

        @Schema(description = "해당 월 총 지출(원)", example = "80000")
        long totalAmount,

        @Schema(description = "전월 총 지출(원)", example = "75000")
        long previousMonthAmount,

        @Schema(description = "전월 대비 증감액(원)", example = "5000")
        long changeAmount,

        @Schema(description = "전월 대비 증감률(%)", example = "6.67")
        double changeRate,

        @Schema(description = "월간 구독 지출 합계(원)", example = "50000")
        long monthlySubscriptionAmount,

        @Schema(description = "연간 구독의 월 환산 합계(원)", example = "30000")
        long annualSubscriptionMonthlyConvertedAmount,

        @Schema(description = "이번 달 실제 결제 금액(연간 구독은 결제월에만 전액 반영, 원)", example = "137000")
        long actualAmount
) {
}
```

- [ ] **Step 4: `ExpenseService.getMonthlyExpense`에서 값 계산해 채우기**

`ExpenseService.java`의 `getMonthlyExpense` 메서드를 아래로 교체:

```java
    @Transactional(readOnly = true)
    public MonthlyExpenseResponse getMonthlyExpense(Long userId, int year, int month) {
        YearMonth target = parseYearMonth(year, month);
        List<Subscription> all = loadAllSubscriptions(userId);

        List<Subscription> currentMonth = filterByMonth(all, target);
        List<Subscription> previousMonth = filterByMonth(all, target.minusMonths(1));

        long totalAmount = sumMonthlyAmount(currentMonth);
        long previousAmount = sumMonthlyAmount(previousMonth);
        long changeAmount = totalAmount - previousAmount;
        double changeRate = calculateChangeRate(totalAmount, previousAmount);

        long monthly = sumMonthlyAmount(filterByCycle(currentMonth, BillingCycle.MONTHLY));
        long yearlyConverted = sumMonthlyAmount(filterByCycle(currentMonth, BillingCycle.YEARLY));
        long actualAmount = sumActualAmount(currentMonth, target);

        return new MonthlyExpenseResponse(
                target.getYear(), target.getMonthValue(),
                totalAmount, previousAmount, changeAmount, changeRate,
                monthly, yearlyConverted, actualAmount
        );
    }
```

그리고 `sumMonthlyAmount` private 메서드 바로 아래에 새 private 메서드를 추가:

```java
    private long sumActualAmount(List<Subscription> subscriptions, YearMonth target) {
        return subscriptions.stream()
                .mapToLong(s -> MonthlyAmountCalculator.actualAmount(s, target))
                .sum();
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "*ExpenseControllerTest"`
Expected: PASS (기존 테스트 + 새 3개 테스트 전부)

- [ ] **Step 6: 전체 테스트 스위트 실행**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — 리팩터링 두 곳(Task 3, 4)과 신규 필드 추가가 프로젝트 전체에서 회귀 없이 통과

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/scrumble/gudocs/expense/dto/response/MonthlyExpenseResponse.java src/main/java/com/scrumble/gudocs/expense/service/ExpenseService.java src/test/java/com/scrumble/gudocs/expense/controller/ExpenseControllerTest.java
git commit -m "feat: 월별 지출 응답에 실결제금액(actualAmount) 필드 추가"
```

---

## Self-Review Notes

- **스펙 커버리지**: 판단 기준(MONTHLY 전액/YEARLY 결제월만 전액, 연도 무시) → Task 2. 응답에 필드 하나만 추가(기존 필드 유지) → Task 5. `MonthlyAmountCalculator` 단일 소스화(스펙의 구현 섹션) → Task 1, 3, 4. 범위 밖으로 명시된 카테고리별/추이/상세 API, 전월 대비 증감, FE 토글 UI는 이번 계획에 포함하지 않음 (스펙과 일치).
- **플레이스홀더 스캔**: 없음 — 모든 스텝에 실제 코드 포함.
- **타입/시그니처 일관성**: `MonthlyAmountCalculator.monthlyAmount(Subscription): long`, `actualAmount(Subscription, YearMonth): long` 이름과 시그니처가 Task 1~5 전체에서 동일하게 사용됨.
