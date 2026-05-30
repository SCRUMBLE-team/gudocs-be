package com.scrumble.gudocs.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.auth.dto.LoginRequest;
import com.scrumble.gudocs.auth.dto.SignupRequest;
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
class NotificationControllerTest {

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
                        new SignupRequest("테스터", "noti@example.com", "Password1!"))));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("noti@example.com", "Password1!"))))
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession();
    }

    private void 구독_등록(String name, int billingDay) throws Exception {
        SubscriptionCreateRequest req = new SubscriptionCreateRequest(
                name, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, billingDay, null, PaymentMethod.CARD);
        mockMvc.perform(post("/api/subscriptions")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }

    @Test
    void 알림_조회_빈_목록() throws Exception {
        mockMvc.perform(get("/api/notifications/upcoming").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 알림_조회_7일_이내_구독_포함() throws Exception {
        // billingDay가 오늘이면 daysUntil=0 → 알림 포함
        int todayDay = LocalDate.now().getDayOfMonth();
        구독_등록("Netflix", todayDay);

        mockMvc.perform(get("/api/notifications/upcoming").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].serviceName").value("Netflix"))
                .andExpect(jsonPath("$.data[0].daysUntilBilling").value(0));
    }

    @Test
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/notifications/upcoming"))
                .andExpect(status().isUnauthorized());
    }
}
