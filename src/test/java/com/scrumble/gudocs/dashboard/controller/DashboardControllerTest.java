package com.scrumble.gudocs.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

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
        session = TestSessions.loginNew(userRepository, socialAccountRepository, "테스터", "dash@example.com");
    }

    private void 구독_등록(String name, SubscriptionCategory category, long price,
                         BillingCycle cycle, int day, Integer month) throws Exception {
        LocalDate firstBillingDate = LocalDate.of(2025, month != null ? month : 1, day);
        SubscriptionCreateRequest req = new SubscriptionCreateRequest(
                name, category, price, cycle, firstBillingDate, PaymentMethod.CARD);
        mockMvc.perform(post("/api/subscriptions")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }

    @Test
    void 대시보드_조회_성공_구독_없는_경우() throws Exception {
        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.monthlyTotalExpense").value(0))
                .andExpect(jsonPath("$.data.activeSubscriptionCount").value(0))
                .andExpect(jsonPath("$.data.recentSubscriptions").isArray())
                .andExpect(jsonPath("$.data.categorySummaries").isArray());
    }

    @Test
    void 대시보드_조회_성공_구독_있는_경우() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeSubscriptionCount").value(2))
                .andExpect(jsonPath("$.data.monthlyTotalExpense").value(27000))
                .andExpect(jsonPath("$.data.recentSubscriptions").isArray())
                .andExpect(jsonPath("$.data.categorySummaries").isArray());
    }

    @Test
    void 대시보드_최근_구독_최대_3개() throws Exception {
        구독_등록("Netflix", SubscriptionCategory.OTT, 17000L, BillingCycle.MONTHLY, 15, null);
        구독_등록("Spotify", SubscriptionCategory.MUSIC, 10000L, BillingCycle.MONTHLY, 5, null);
        구독_등록("YouTube", SubscriptionCategory.OTT, 14900L, BillingCycle.MONTHLY, 20, null);
        구독_등록("iCloud", SubscriptionCategory.CLOUD, 1100L, BillingCycle.MONTHLY, 25, null);

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentSubscriptions.length()").value(3));
    }

    @Test
    void 대시보드_연간_구독_월_환산() throws Exception {
        구독_등록("Adobe", SubscriptionCategory.DESIGN, 120000L, BillingCycle.YEARLY, 1, 3);

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyTotalExpense").value(10000));
    }

    @Test
    void 미인증_대시보드_401() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
    }

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
}
