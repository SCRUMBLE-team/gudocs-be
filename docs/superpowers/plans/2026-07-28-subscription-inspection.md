# 구독 점검(미사용/중복 감지) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/dashboard/inspection`을 추가해 미사용 구독 후보(6개월 이상 `updatedAt` 미변경 ACTIVE 구독)와 카테고리 중복 구독 후보(ETC 제외, 같은 카테고리 ACTIVE 2개 이상)를 조회한다.

**Architecture:** 새 도메인 없이 기존 `dashboard` 도메인에 `DashboardService.getInspection(Long userId)`를 추가한다. 이미 존재하는 `MonthlyAmountCalculator.monthlyAmount(Subscription)`(PR #28)를 재사용해 각 후보의 월 환산 금액을 계산한다. 절약액 합산은 프론트 책임이라 서버는 후보 목록만 내려주고 저장/무시 상태는 갖지 않는다.

**Tech Stack:** Spring Boot 3.5 / Java 21, JUnit 5 + AssertJ + Mockito(MockitoExtension), `@SpringBootTest` + `MockMvc` 통합 테스트(H2), `org.springframework.test.util.ReflectionTestUtils`(JPA auditing 필드 테스트 주입용)

## Global Constraints

- 새 의존성 추가 금지 — 기존 스택만 사용
- 다른 사용자 데이터 접근 불가 — `@CurrentUserId Long userId` 기준으로만 조회
- 새 API 추가 시 테스트 필수
- 서버는 절약액을 계산하지 않는다 — 각 후보의 `monthlyAmount`만 내려준다
- 점검 결과는 DB에 저장하지 않는다 (매 요청 실시간 계산)
- 커밋은 태스크 단위로 자주

---

## 참고: 현재 관련 코드 상태

- `DashboardService.java`는 이미 `MonthlyAmountCalculator.monthlyAmount(Subscription)`을 쓰도록 리팩터링돼 있다 (PR #28). 새로 추출할 것 없음.
- `Subscription`은 `@Builder`(`@SuperBuilder` 아님)라 `BaseEntity`의 `createdAt`/`updatedAt`은 빌더로 설정 불가 — JPA가 영속화 시점에만 채운다. 단위 테스트에서 `updatedAt`을 특정 값으로 만들려면 `ReflectionTestUtils.setField(subscription, "updatedAt", value)`를 쓴다.
- `DashboardServiceTest`는 `@Mock` 기반 순수 단위 테스트다. `getDashboard(Long userId, LocalDate today)` 패키지 프라이빗 오버로드로 기준 날짜를 주입하는 기존 패턴을 그대로 따른다.

---

### Task 1: `DashboardService.getInspection` — DTO + 서비스 로직

**Files:**
- Create: `src/main/java/com/scrumble/gudocs/dashboard/dto/InspectionResponse.java`
- Create: `src/main/java/com/scrumble/gudocs/dashboard/dto/UnusedSubscriptionCandidate.java`
- Create: `src/main/java/com/scrumble/gudocs/dashboard/dto/DuplicateCategoryGroup.java`
- Create: `src/main/java/com/scrumble/gudocs/dashboard/dto/DuplicateSubscriptionItem.java`
- Modify: `src/main/java/com/scrumble/gudocs/dashboard/service/DashboardService.java`
- Modify: `src/test/java/com/scrumble/gudocs/dashboard/service/DashboardServiceTest.java`

**Interfaces:**
- Consumes: `MonthlyAmountCalculator.monthlyAmount(Subscription): long` (기존, PR #28)
- Produces: `DashboardService.getInspection(Long userId): InspectionResponse` (public, `LocalDate.now()` 사용) / `DashboardService.getInspection(Long userId, LocalDate today): InspectionResponse` (package-private, 테스트용). `InspectionResponse.unusedCandidates(): List<UnusedSubscriptionCandidate>`, `InspectionResponse.duplicateGroups(): List<DuplicateCategoryGroup>`

- [ ] **Step 1: 실패하는 테스트 작성**

`DashboardServiceTest.java` 상단 import에 아래 2개 추가:

```java
import com.scrumble.gudocs.dashboard.dto.InspectionResponse;
import org.springframework.test.util.ReflectionTestUtils;
```

파일 맨 아래, 마지막 `}` 앞에 아래 테스트들을 추가한다 (기존 `구독_없으면_최근_구독_빈_목록()` 테스트 다음):

```java
    // ─── getInspection: unusedCandidates ───────────────────────────────────────

    @Test
    void 미사용_후보_updatedAt_6개월_전이면_포함() {
        User u = user();
        Subscription old = monthly("Adobe", SubscriptionCategory.DESIGN, 24000L, 10);
        ReflectionTestUtils.setField(old, "updatedAt", TODAY.minusMonths(6).atStartOfDay());
        setupUser(u, List.of(old));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).hasSize(1);
        assertThat(response.unusedCandidates().get(0).serviceName()).isEqualTo("Adobe");
    }

    @Test
    void 미사용_후보_updatedAt_6개월에서_하루_모자라면_제외() {
        User u = user();
        Subscription recent = monthly("Adobe", SubscriptionCategory.DESIGN, 24000L, 10);
        ReflectionTestUtils.setField(recent, "updatedAt", TODAY.minusMonths(6).plusDays(1).atStartOfDay());
        setupUser(u, List.of(recent));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).isEmpty();
    }

    @Test
    void PAUSED_구독은_미사용_후보에서_제외() {
        User u = user();
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Spotify").category(SubscriptionCategory.MUSIC)
                .price(10000L).billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 10))
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        ReflectionTestUtils.setField(paused, "updatedAt", TODAY.minusMonths(12).atStartOfDay());
        setupUser(u, List.of(paused));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).isEmpty();
    }

    @Test
    void 미사용_후보_monthlyAmount는_YEARLY_12로_나눈_값() {
        User u = user();
        Subscription old = yearly("Adobe", SubscriptionCategory.DESIGN, 120000L, 1, 3);
        ReflectionTestUtils.setField(old, "updatedAt", TODAY.minusMonths(7).atStartOfDay());
        setupUser(u, List.of(old));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates().get(0).monthlyAmount()).isEqualTo(10000L);
    }

    // ─── getInspection: duplicateGroups ────────────────────────────────────────

    @Test
    void 같은_카테고리_2개_이상이면_중복_후보() {
        User u = user();
        setupUser(u, List.of(
                monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15),
                monthly("Watcha", SubscriptionCategory.OTT, 12900L, 5)
        ));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).hasSize(1);
        assertThat(response.duplicateGroups().get(0).category()).isEqualTo(SubscriptionCategory.OTT);
        assertThat(response.duplicateGroups().get(0).subscriptions()).hasSize(2);
    }

    @Test
    void 같은_카테고리_1개면_중복_후보_아님() {
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).isEmpty();
    }

    @Test
    void ETC_카테고리는_2개_이상이어도_중복_후보_제외() {
        User u = user();
        setupUser(u, List.of(
                monthly("A", SubscriptionCategory.ETC, 5000L, 15),
                monthly("B", SubscriptionCategory.ETC, 3000L, 5)
        ));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).isEmpty();
    }

    @Test
    void PAUSED_구독은_중복_후보에서_제외() {
        User u = user();
        Subscription active = monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15);
        Subscription paused = Subscription.builder()
                .user(u).serviceName("Watcha").category(SubscriptionCategory.OTT)
                .price(12900L).billingCycle(BillingCycle.MONTHLY).firstBillingDate(LocalDate.of(2025, 1, 5))
                .paymentMethod(PaymentMethod.CARD).status(SubscriptionStatus.PAUSED).build();
        setupUser(u, List.of(active, paused));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.duplicateGroups()).isEmpty();
    }

    // ─── getInspection: 후보 없음 ─────────────────────────────────────────────────

    @Test
    void 후보_없으면_양쪽_다_빈_배열() {
        User u = user();
        setupUser(u, List.of(monthly("Netflix", SubscriptionCategory.OTT, 17000L, 15)));

        InspectionResponse response = dashboardService.getInspection(1L, TODAY);

        assertThat(response.unusedCandidates()).isEmpty();
        assertThat(response.duplicateGroups()).isEmpty();
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*DashboardServiceTest"`
Expected: FAIL — `getInspection` 메서드와 `InspectionResponse` 등 DTO가 없어서 컴파일 에러

- [ ] **Step 3: DTO 작성**

`src/main/java/com/scrumble/gudocs/dashboard/dto/UnusedSubscriptionCandidate.java`:

```java
package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.util.MonthlyAmountCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record UnusedSubscriptionCandidate(
        @Schema(description = "구독 ID", example = "3")
        Long id,

        @Schema(description = "서비스명", example = "Adobe CC")
        String serviceName,

        @Schema(description = "카테고리", example = "DESIGN")
        SubscriptionCategory category,

        @Schema(description = "결제 금액(원)", example = "24000")
        Long price,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "월 환산 금액(원)", example = "24000")
        Long monthlyAmount,

        @Schema(description = "마지막 수정 일시", example = "2026-01-10T09:00:00")
        LocalDateTime updatedAt
) {
    public static UnusedSubscriptionCandidate from(Subscription subscription) {
        return new UnusedSubscriptionCandidate(
                subscription.getId(),
                subscription.getServiceName(),
                subscription.getCategory(),
                subscription.getPrice(),
                subscription.getBillingCycle(),
                MonthlyAmountCalculator.monthlyAmount(subscription),
                subscription.getUpdatedAt()
        );
    }
}
```

`src/main/java/com/scrumble/gudocs/dashboard/dto/DuplicateSubscriptionItem.java`:

```java
package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.util.MonthlyAmountCalculator;
import io.swagger.v3.oas.annotations.media.Schema;

public record DuplicateSubscriptionItem(
        @Schema(description = "구독 ID", example = "1")
        Long id,

        @Schema(description = "서비스명", example = "Netflix")
        String serviceName,

        @Schema(description = "결제 금액(원)", example = "17000")
        Long price,

        @Schema(description = "결제 주기", example = "MONTHLY")
        BillingCycle billingCycle,

        @Schema(description = "월 환산 금액(원)", example = "17000")
        Long monthlyAmount
) {
    public static DuplicateSubscriptionItem from(Subscription subscription) {
        return new DuplicateSubscriptionItem(
                subscription.getId(),
                subscription.getServiceName(),
                subscription.getPrice(),
                subscription.getBillingCycle(),
                MonthlyAmountCalculator.monthlyAmount(subscription)
        );
    }
}
```

`src/main/java/com/scrumble/gudocs/dashboard/dto/DuplicateCategoryGroup.java`:

```java
package com.scrumble.gudocs.dashboard.dto;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DuplicateCategoryGroup(
        @Schema(description = "카테고리", example = "OTT")
        SubscriptionCategory category,

        @Schema(description = "해당 카테고리의 ACTIVE 구독 목록")
        List<DuplicateSubscriptionItem> subscriptions
) {
}
```

`src/main/java/com/scrumble/gudocs/dashboard/dto/InspectionResponse.java`:

```java
package com.scrumble.gudocs.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record InspectionResponse(
        @Schema(description = "미사용 구독 후보 (6개월 이상 정보 미변경 ACTIVE 구독)")
        List<UnusedSubscriptionCandidate> unusedCandidates,

        @Schema(description = "카테고리 중복 구독 후보 (ETC 제외, 같은 카테고리 ACTIVE 2개 이상)")
        List<DuplicateCategoryGroup> duplicateGroups
) {
}
```

- [ ] **Step 4: `DashboardService`에 `getInspection` 추가**

`DashboardService.java` import 목록에 추가:

```java
import com.scrumble.gudocs.dashboard.dto.DuplicateCategoryGroup;
import com.scrumble.gudocs.dashboard.dto.DuplicateSubscriptionItem;
import com.scrumble.gudocs.dashboard.dto.InspectionResponse;
import com.scrumble.gudocs.dashboard.dto.UnusedSubscriptionCandidate;
```

`java.time.LocalDate` import는 이미 있음. `getDashboard(Long userId, LocalDate today)` 메서드 바로 아래에 추가:

```java
    @Transactional(readOnly = true)
    public InspectionResponse getInspection(Long userId) {
        return getInspection(userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    InspectionResponse getInspection(Long userId, LocalDate today) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Subscription> active = subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .toList();

        return new InspectionResponse(findUnusedCandidates(active, today), findDuplicateGroups(active));
    }

    private List<UnusedSubscriptionCandidate> findUnusedCandidates(List<Subscription> active, LocalDate today) {
        LocalDate threshold = today.minusMonths(6);
        return active.stream()
                .filter(s -> s.getUpdatedAt() != null && !s.getUpdatedAt().toLocalDate().isAfter(threshold))
                .map(UnusedSubscriptionCandidate::from)
                .toList();
    }

    private List<DuplicateCategoryGroup> findDuplicateGroups(List<Subscription> active) {
        Map<SubscriptionCategory, List<Subscription>> grouped = active.stream()
                .filter(s -> s.getCategory() != SubscriptionCategory.ETC)
                .collect(Collectors.groupingBy(Subscription::getCategory));

        return grouped.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(entry -> new DuplicateCategoryGroup(
                        entry.getKey(),
                        entry.getValue().stream().map(DuplicateSubscriptionItem::from).toList()
                ))
                .toList();
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "*DashboardServiceTest"`
Expected: PASS (기존 테스트 + 신규 9개 전부)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/scrumble/gudocs/dashboard/dto/InspectionResponse.java src/main/java/com/scrumble/gudocs/dashboard/dto/UnusedSubscriptionCandidate.java src/main/java/com/scrumble/gudocs/dashboard/dto/DuplicateCategoryGroup.java src/main/java/com/scrumble/gudocs/dashboard/dto/DuplicateSubscriptionItem.java src/main/java/com/scrumble/gudocs/dashboard/service/DashboardService.java src/test/java/com/scrumble/gudocs/dashboard/service/DashboardServiceTest.java
git commit -m "feat: DashboardService.getInspection 추가 (미사용/카테고리 중복 구독 후보)"
```

---

### Task 2: `GET /api/dashboard/inspection` API 노출

**Files:**
- Modify: `src/main/java/com/scrumble/gudocs/dashboard/controller/DashboardApi.java`
- Modify: `src/main/java/com/scrumble/gudocs/dashboard/controller/DashboardController.java`
- Modify: `src/test/java/com/scrumble/gudocs/dashboard/controller/DashboardControllerTest.java`

**Interfaces:**
- Consumes: Task 1의 `DashboardService.getInspection(Long userId): InspectionResponse`
- Produces: `GET /api/dashboard/inspection` (세션 인증 필요) — `ApiResponse<InspectionResponse>`

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`DashboardControllerTest.java`의 마지막 테스트(`미인증_대시보드_401`) 앞에 추가:

```java
    @Test
    void 구독_점검_카테고리_중복_후보_반환() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Watcha", SubscriptionCategory.OTT, 12900L, BillingCycle.MONTHLY, 5, null);

        mockMvc.perform(get("/api/dashboard/inspection").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicateGroups.length()").value(1))
                .andExpect(jsonPath("$.data.duplicateGroups[0].category").value("OTT"))
                .andExpect(jsonPath("$.data.duplicateGroups[0].subscriptions.length()").value(2))
                .andExpect(jsonPath("$.data.unusedCandidates").isArray());
    }

    @Test
    void 구독_점검_후보_없으면_빈_배열() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);

        mockMvc.perform(get("/api/dashboard/inspection").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unusedCandidates.length()").value(0))
                .andExpect(jsonPath("$.data.duplicateGroups.length()").value(0));
    }

    @Test
    void 미인증_구독_점검_401() throws Exception {
        mockMvc.perform(get("/api/dashboard/inspection"))
                .andExpect(status().isUnauthorized());
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*DashboardControllerTest"`
Expected: FAIL — `/api/dashboard/inspection` 경로가 없어서 404

- [ ] **Step 3: `DashboardApi`에 시그니처 추가**

`DashboardApi.java`를 아래로 교체:

```java
package com.scrumble.gudocs.dashboard.controller;

import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.dashboard.dto.InspectionResponse;
import com.scrumble.gudocs.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import com.scrumble.gudocs.global.security.CurrentUserId;

@Tag(name = "Dashboard", description = "메인 대시보드 API")
@SecurityRequirement(name = "cookieAuth")
public interface DashboardApi {

    @Operation(summary = "대시보드 조회", description = "이번 달 총 지출, 카테고리별 요약, 결제 예정 알림 등 메인 대시보드 데이터를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(@CurrentUserId Long userId);

    @Operation(summary = "구독 점검", description = "미사용 구독 후보(6개월 이상 정보 미변경)와 카테고리 중복 구독 후보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<InspectionResponse>> getInspection(@CurrentUserId Long userId);
}
```

- [ ] **Step 4: `DashboardController`에 엔드포인트 추가**

`DashboardController.java`를 아래로 교체:

```java
package com.scrumble.gudocs.dashboard.controller;

import com.scrumble.gudocs.dashboard.dto.DashboardResponse;
import com.scrumble.gudocs.dashboard.dto.InspectionResponse;
import com.scrumble.gudocs.dashboard.service.DashboardService;
import com.scrumble.gudocs.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.scrumble.gudocs.global.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @CurrentUserId Long userId) {
        DashboardResponse response = dashboardService.getDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success("대시보드 조회 성공", response));
    }

    @Override
    @GetMapping("/inspection")
    public ResponseEntity<ApiResponse<InspectionResponse>> getInspection(
            @CurrentUserId Long userId) {
        InspectionResponse response = dashboardService.getInspection(userId);
        return ResponseEntity.ok(ApiResponse.success("구독 점검에 성공했습니다.", response));
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "*DashboardControllerTest"`
Expected: PASS (기존 테스트 + 신규 3개)

- [ ] **Step 6: 전체 테스트 스위트 실행**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/scrumble/gudocs/dashboard/controller/DashboardApi.java src/main/java/com/scrumble/gudocs/dashboard/controller/DashboardController.java src/test/java/com/scrumble/gudocs/dashboard/controller/DashboardControllerTest.java
git commit -m "feat: GET /api/dashboard/inspection 엔드포인트 추가"
```

---

## Self-Review Notes

- **스펙 커버리지**: 대상 필터(ACTIVE만) → Task 1 `getInspection`의 `active` 필터. 미사용 판단(`updatedAt` 6개월, 경계값) → Task 1 `findUnusedCandidates` + 경계 테스트 2건. 중복 판단(ETC 제외, 그룹 2개 이상) → Task 1 `findDuplicateGroups` + 테스트 3건. 절약액은 서버가 계산하지 않음 → DTO에 `monthlyAmount`만 있고 합산 필드 없음. 저장 없음 → 매 요청 `SubscriptionRepository` 조회 후 즉시 계산, 별도 엔티티/테이블 없음. API 응답 스펙(엔드포인트, 필드명, 빈 배열) → Task 2. `USER_NOT_FOUND`/`401` 에러 → Task 1(`USER_NOT_FOUND`는 기존 `getDashboard`와 동일 패턴 재사용), 401은 기존 세션 인증 필터가 전역 처리(다른 API와 동일, 별도 구현 불필요).
- **플레이스홀더 스캔**: 없음 — 모든 스텝에 실제 코드 포함.
- **타입/시그니처 일관성**: `DashboardService.getInspection(Long, LocalDate): InspectionResponse`, `InspectionResponse.unusedCandidates()/duplicateGroups()`, `UnusedSubscriptionCandidate.from(Subscription)`, `DuplicateSubscriptionItem.from(Subscription)` 이름과 시그니처가 Task 1~2 전체에서 동일하게 사용됨.
