package com.scrumble.gudocs.expense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, "테스터", "expense@example.com");
    }

    private long 구독_등록(String name, SubscriptionCategory category, long price,
                          BillingCycle cycle, int day, Integer month) throws Exception {
        return 구독_등록(name, category, price, cycle, LocalDate.of(2025, month != null ? month : 1, day));
    }

    /**
     * 최초 결제일을 과거로 주면 등록 시점에 그 기간의 결제 기록이 채워진다(BillingRecordService.backfillPastBillings).
     * 그래서 이 헬퍼로 등록한 구독은 곧바로 과거 몇 달치 지출 이력을 갖는다.
     */
    private long 구독_등록(String name, SubscriptionCategory category, long price,
                          BillingCycle cycle, LocalDate firstBillingDate) throws Exception {
        SubscriptionCreateRequest req = new SubscriptionCreateRequest(
                name, null, category, price, cycle, firstBillingDate);
        MvcResult result = mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void 구독_일시정지(long subscriptionId) throws Exception {
        mockMvc.perform(put("/api/subscriptions/" + subscriptionId + "/status")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SubscriptionStatusUpdateRequest(SubscriptionStatus.PAUSED))));
    }

    private void 구독_재개(long subscriptionId) throws Exception {
        mockMvc.perform(put("/api/subscriptions/" + subscriptionId + "/status")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SubscriptionStatusUpdateRequest(SubscriptionStatus.ACTIVE))));
    }

    private void 구독_가격_수정(long subscriptionId, long price) throws Exception {
        mockMvc.perform(put("/api/subscriptions/" + subscriptionId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubscriptionUpdateRequest(
                                "Netflix", null, SubscriptionCategory.OTT, price,
                                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)))))
                .andExpect(status().isOk());
    }

    private void 구독_카테고리_수정(long subscriptionId, SubscriptionCategory category) throws Exception {
        mockMvc.perform(put("/api/subscriptions/" + subscriptionId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubscriptionUpdateRequest(
                                "Netflix", null, category, 17000L,
                                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)))))
                .andExpect(status().isOk());
    }

    private void 구독_삭제(long subscriptionId) throws Exception {
        mockMvc.perform(delete("/api/subscriptions/" + subscriptionId).session(session));
    }

    private YearMonth 현재월() {
        return YearMonth.now();
    }

    /**
     * 매달 1일 청구 예정. 오늘이 며칠이든 이번 달 기록이 생성되므로, 기록 청구액(JSON 호환명
     * {@code actualAmount}) 테스트가 실행 날짜에 따라 흔들리지 않는다. (15일로 두면 1~14일과
     * 15일 이후에 돌릴 때 결과가 달라진다)
     */
    private long 이번달_청구일이_지난_구독(String name, SubscriptionCategory category, long price,
                              BillingCycle cycle) throws Exception {
        return 구독_등록(name, category, price, cycle, LocalDate.of(2025, 1, 1));
    }

    private ResultActions 월별_지출(YearMonth ym) throws Exception {
        return mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                .session(session)
                .param("year", String.valueOf(ym.getYear()))
                .param("month", String.valueOf(ym.getMonthValue())));
    }

    @Test
    void 월별_지출_분석_조회_성공() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.year").value(now.getYear()))
                .andExpect(jsonPath("$.data.month").value(now.getMonthValue()))
                .andExpect(jsonPath("$.data.totalAmount").value(27000))
                .andExpect(jsonPath("$.data.monthlySubscriptionAmount").value(27000))
                .andExpect(jsonPath("$.data.annualSubscriptionMonthlyConvertedAmount").value(0));
    }

    @Test
    void 월별_지출_분석_연간구독_월환산_반영() throws Exception {
        구독_등록("Adobe", SubscriptionCategory.DESIGN, 120000L, BillingCycle.YEARLY, 1, 3);
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000))
                .andExpect(jsonPath("$.data.monthlySubscriptionAmount").value(17000))
                .andExpect(jsonPath("$.data.annualSubscriptionMonthlyConvertedAmount").value(10000));
    }

    /**
     * 원래 물음: 정지하면 과거에 생성된 청구 스냅샷이 남는가.
     *
     * <p>남는다. 정지는 과거 기록을 건드리지 않고 앞으로 새 기록이 안 생길 뿐이다. 예전에는
     * {@code paused_at} 한 값으로 과거를 되짚어서, 재개하는 순간 정지 구간이 결제로 둔갑했다.
     */
    @Test
    void 정지해도_과거_결제_기록은_그대로_남는다() throws Exception {
        long pausedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_일시정지(pausedId);

        월별_지출(현재월().minusMonths(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000));
    }

    /** 정지/재개를 반복해도 마찬가지다. 이 시나리오가 예전 구현이 틀리던 지점이다. */
    @Test
    void 정지했다_재개해도_과거_결제_기록은_그대로_남는다() throws Exception {
        long id = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_일시정지(id);
        구독_재개(id);

        월별_지출(현재월().minusMonths(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(17000));
    }

    /** 청구일이 도래해 스냅샷이 생긴 뒤 정지하면 그 달 기록에는 남는다. */
    @Test
    void 이번_달_결제_후_정지하면_그_달_지출에는_남는다() throws Exception {
        long pausedId = 이번달_청구일이_지난_구독("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY);
        이번달_청구일이_지난_구독("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY);
        구독_일시정지(pausedId);

        월별_지출(현재월())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000))
                .andExpect(jsonPath("$.data.actualAmount").value(27000));
    }

    @Test
    void 삭제해도_과거_결제_기록은_그대로_남는다() throws Exception {
        long deletedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_삭제(deletedId);

        월별_지출(현재월().minusMonths(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000));
    }

    /** 삭제한 구독은 앞으로 결제되지 않는다 — 다음 달에는 예정 결제도 잡히지 않는다. */
    @Test
    void 삭제하면_이후_결제는_기록되지_않는다() throws Exception {
        long deletedId = 이번달_청구일이_지난_구독("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY);
        구독_삭제(deletedId);

        월별_지출(현재월().plusMonths(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(0));
    }

    /** 가격을 바꿔도 이미 결제된 과거 금액은 따라 움직이지 않는다. 스냅샷 모델의 핵심 성질. */
    @Test
    void 가격을_수정해도_과거_결제액은_바뀌지_않는다() throws Exception {
        long id = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_가격_수정(id, 25000L);

        월별_지출(현재월().minusMonths(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(17000));
    }

    @Test
    void 월별_지출_분석_전월_0원_changeRate_0() throws Exception {
        // 이번 달에 처음 결제가 시작된 구독 → 전월에는 결제 기록이 없다.
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 현재월().atDay(1));

        월별_지출(현재월())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousMonthAmount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(17000))
                .andExpect(jsonPath("$.data.changeAmount").value(17000))
                .andExpect(jsonPath("$.data.changeRate").value(0.0));
    }

    @Test
    void 월별_지출_분석_기록청구액_MONTHLY만_있으면_totalAmount와_동일() throws Exception {
        이번달_청구일이_지난_구독("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY);
        이번달_청구일이_지난_구독("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY);

        월별_지출(현재월())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(27000));
    }

    @Test
    void 월별_지출_분석_기록청구액_YEARLY_청구월과_같으면_전액() throws Exception {
        YearMonth now = 현재월();
        // 작년 이번 달 1일부터 연 1회 결제 → 이번 달 1일에 갱신 결제가 일어난 상태
        구독_등록("Adobe", SubscriptionCategory.DESIGN, 120000L, BillingCycle.YEARLY,
                now.minusYears(1).atDay(1));
        이번달_청구일이_지난_구독("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY);

        월별_지출(now)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000))
                .andExpect(jsonPath("$.data.monthlySubscriptionAmount").value(17000))
                .andExpect(jsonPath("$.data.annualSubscriptionMonthlyConvertedAmount").value(10000))
                .andExpect(jsonPath("$.data.actualAmount").value(137000));
    }

    /**
     * 연간 구독은 청구월이 아닌 달에는 기록 청구액이 0이지만, 월 환산 부담(10,000원)은 계속 잡힌다.
     * 결제월부터 12개월에 분산되기 때문 — 두 숫자가 다른 질문에 답한다는 것을 확인한다.
     */
    @Test
    void 월별_지출_분석_기록청구액_YEARLY_청구월과_다르면_0원() throws Exception {
        YearMonth now = 현재월();
        구독_등록("Adobe", SubscriptionCategory.DESIGN, 120000L, BillingCycle.YEARLY,
                now.minusMonths(3).atDay(1));
        이번달_청구일이_지난_구독("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY);

        월별_지출(now)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualAmount").value(17000))
                .andExpect(jsonPath("$.data.annualSubscriptionMonthlyConvertedAmount").value(10000));
    }

    @Test
    void 월별_지출_분석_month_범위_초과_400() throws Exception {
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", "2026")
                        .param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 월별_지출_분석_month_0_400() throws Exception {
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", "2026")
                        .param("month", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 미인증_월별_지출_분석_401() throws Exception {
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .param("year", "2026")
                        .param("month", "5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 카테고리별_지출_분석_조회_성공() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("YouTube", SubscriptionCategory.OTT, 14900L, BillingCycle.MONTHLY, 10, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/categories")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(41900))
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.categories[0].category").value("OTT"))
                .andExpect(jsonPath("$.data.categories[0].categoryName").value("영상 스트리밍"))
                .andExpect(jsonPath("$.data.categories[0].amount").value(31900))
                .andExpect(jsonPath("$.data.categories[0].subscriptionCount").value(2));
    }

    @Test
    void 카테고리별_지출_분석_정지해도_과거_달의_카테고리는_그대로() throws Exception {
        long pausedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_일시정지(pausedId);

        YearMonth prev = 현재월().minusMonths(1);
        mockMvc.perform(get("/api/subscriptions/expenses/categories")
                        .session(session)
                        .param("year", String.valueOf(prev.getYear()))
                        .param("month", String.valueOf(prev.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000))
                .andExpect(jsonPath("$.data.categories.length()").value(2));
    }

    /** 카테고리도 청구 스냅샷이라, 지금 카테고리를 바꿔도 과거 달의 분류는 그대로다. */
    @Test
    void 카테고리를_수정해도_과거_달의_분류는_바뀌지_않는다() throws Exception {
        long id = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_카테고리_수정(id, SubscriptionCategory.ETC);

        YearMonth prev = 현재월().minusMonths(1);
        mockMvc.perform(get("/api/subscriptions/expenses/categories")
                        .session(session)
                        .param("year", String.valueOf(prev.getYear()))
                        .param("month", String.valueOf(prev.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].category").value("OTT"));
    }

    @Test
    void 최근_6개월_지출_추이_조회_성공() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/trends")
                        .session(session)
                        .param("baseYear", String.valueOf(now.getYear()))
                        .param("baseMonth", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.baseYear").value(now.getYear()))
                .andExpect(jsonPath("$.data.baseMonth").value(now.getMonthValue()))
                .andExpect(jsonPath("$.data.monthlyTrends.length()").value(6))
                .andExpect(jsonPath("$.data.monthlyTrends[5].totalAmount").value(17000));
    }

    @Test
    void 월별_상세_지출_내역_조회_성공() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Adobe", SubscriptionCategory.DESIGN, 120000L, BillingCycle.YEARLY, 1, 3);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/monthly/details")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(27000))
                .andExpect(jsonPath("$.data.subscriptions.length()").value(2))
                .andExpect(jsonPath("$.data.subscriptions[0].serviceName").value("Netflix"))
                .andExpect(jsonPath("$.data.subscriptions[0].appliedMonthlyAmount").value(17000))
                .andExpect(jsonPath("$.data.subscriptions[1].serviceName").value("Adobe"))
                .andExpect(jsonPath("$.data.subscriptions[1].originalPrice").value(120000))
                .andExpect(jsonPath("$.data.subscriptions[1].appliedMonthlyAmount").value(10000));
    }

    /** 삭제한 구독도 과거 달의 상세 내역에는 남고, 삭제됐다는 표시만 붙는다. */
    @Test
    void 월별_상세_지출_내역_삭제된_구독도_과거_달에는_남는다() throws Exception {
        long deletedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_삭제(deletedId);

        YearMonth prev = 현재월().minusMonths(1);
        mockMvc.perform(get("/api/subscriptions/expenses/monthly/details")
                        .session(session)
                        .param("year", String.valueOf(prev.getYear()))
                        .param("month", String.valueOf(prev.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriptions.length()").value(1))
                .andExpect(jsonPath("$.data.subscriptions[0].serviceName").value("Netflix"))
                .andExpect(jsonPath("$.data.subscriptions[0].deleted").value(true));
    }

    @Test
    void 삭제_후_재조회는_404() throws Exception {
        long deletedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_삭제(deletedId);

        mockMvc.perform(get("/api/subscriptions/" + deletedId).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void 지출_상세에_serviceCode가_포함된다() throws Exception {
        // 프론트가 표시 이름이 아니라 code로 로고를 찾을 수 있어야 한다.
        YearMonth now = YearMonth.now();
        mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubscriptionCreateRequest(
                                "넷플릭스", "NETFLIX", SubscriptionCategory.OTT, 17000L,
                                BillingCycle.MONTHLY, now.atDay(1)))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/subscriptions/expenses/monthly/details")
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriptions[0].serviceName").value("넷플릭스"))
                .andExpect(jsonPath("$.data.subscriptions[0].serviceCode").value("NETFLIX"));
    }

    @Test
    void 미인증_카테고리별_지출_401() throws Exception {
        mockMvc.perform(get("/api/subscriptions/expenses/categories")
                        .param("year", "2026")
                        .param("month", "5"))
                .andExpect(status().isUnauthorized());
    }
}
