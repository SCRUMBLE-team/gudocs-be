package com.scrumble.gudocs.expense.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.auth.dto.LoginRequest;
import com.scrumble.gudocs.auth.dto.SignupRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

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

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SignupRequest("테스터", "expense@example.com", "Password1!"))));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("expense@example.com", "Password1!"))))
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession();
    }

    private long 구독_등록(String name, SubscriptionCategory category, long price,
                          BillingCycle cycle, int day, Integer month) throws Exception {
        SubscriptionCreateRequest req = new SubscriptionCreateRequest(
                name, category, price, cycle, day, month, PaymentMethod.CARD);
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

    private void 구독_삭제(long subscriptionId) throws Exception {
        mockMvc.perform(delete("/api/subscriptions/" + subscriptionId).session(session));
    }

    private YearMonth 현재월() {
        return YearMonth.now();
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

    @Test
    void 월별_지출_분석_PAUSED_같은_달은_포함() throws Exception {
        long pausedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_일시정지(pausedId);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000));
    }

    @Test
    void 월별_지출_분석_PAUSED_다음_달부터_제외() throws Exception {
        long pausedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_일시정지(pausedId);

        YearMonth next = 현재월().plusMonths(1);
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(next.getYear()))
                        .param("month", String.valueOf(next.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(10000));
    }

    @Test
    void 월별_지출_분석_DELETE_같은_달은_포함() throws Exception {
        long deletedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_삭제(deletedId);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(27000));
    }

    @Test
    void 월별_지출_분석_DELETE_다음_달부터_제외() throws Exception {
        long deletedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_삭제(deletedId);

        YearMonth next = 현재월().plusMonths(1);
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(next.getYear()))
                        .param("month", String.valueOf(next.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(10000));
    }

    @Test
    void 월별_지출_분석_전월_0원_changeRate_0() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);

        YearMonth now = 현재월();
        mockMvc.perform(get("/api/subscriptions/expenses/monthly")
                        .session(session)
                        .param("year", String.valueOf(now.getYear()))
                        .param("month", String.valueOf(now.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousMonthAmount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(17000))
                .andExpect(jsonPath("$.data.changeAmount").value(17000))
                .andExpect(jsonPath("$.data.changeRate").value(0.0));
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
    void 카테고리별_지출_분석_PAUSED_다음_달부터_제외() throws Exception {
        long pausedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_일시정지(pausedId);

        YearMonth next = 현재월().plusMonths(1);
        mockMvc.perform(get("/api/subscriptions/expenses/categories")
                        .session(session)
                        .param("year", String.valueOf(next.getYear()))
                        .param("month", String.valueOf(next.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(10000))
                .andExpect(jsonPath("$.data.categories.length()").value(1))
                .andExpect(jsonPath("$.data.categories[0].category").value("MUSIC"));
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

    @Test
    void 월별_상세_지출_내역_PAUSED_다음_달부터_제외() throws Exception {
        long pausedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_일시정지(pausedId);

        YearMonth next = 현재월().plusMonths(1);
        mockMvc.perform(get("/api/subscriptions/expenses/monthly/details")
                        .session(session)
                        .param("year", String.valueOf(next.getYear()))
                        .param("month", String.valueOf(next.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriptions.length()").value(1))
                .andExpect(jsonPath("$.data.subscriptions[0].serviceName").value("Spotify"));
    }

    @Test
    void 삭제_후_재조회는_404() throws Exception {
        long deletedId = 구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_삭제(deletedId);

        mockMvc.perform(get("/api/subscriptions/" + deletedId).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void 미인증_카테고리별_지출_401() throws Exception {
        mockMvc.perform(get("/api/subscriptions/expenses/categories")
                        .param("year", "2026")
                        .param("month", "5"))
                .andExpect(status().isUnauthorized());
    }
}
