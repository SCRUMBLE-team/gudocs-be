package com.scrumble.gudocs.subscriptions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.auth.dto.LoginRequest;
import com.scrumble.gudocs.auth.dto.SignupRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.entity.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubscriptionControllerTest {

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
                        new SignupRequest("테스터", "sub@example.com", "Password1!"))));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("sub@example.com", "Password1!"))))
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession();
    }

    private MvcResult 구독_등록(MockHttpSession s, SubscriptionCreateRequest request) throws Exception {
        return mockMvc.perform(post("/api/subscriptions")
                        .session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    private long 구독_ID_추출(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void 구독_등록_성공() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );

        mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceName").value("Netflix"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void 구독_등록_연간결제_월_없음_400() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Adobe", SubscriptionCategory.DESIGN, 60000L,
                BillingCycle.YEARLY, 1, null, PaymentMethod.CARD
        );

        mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 구독_등록_필수값_누락_400() throws Exception {
        String body = "{\"category\":\"OTT\",\"price\":17000,\"billingCycle\":\"MONTHLY\",\"billingDay\":15,\"paymentMethod\":\"CARD\"}";

        mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 구독_등록_월간결제_결제월_입력_400() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, 5, PaymentMethod.CARD
        );

        mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 구독_수정_월간결제_결제월_입력_400() throws Exception {
        SubscriptionCreateRequest createRequest = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        MvcResult createResult = 구독_등록(session, createRequest);
        long id = 구독_ID_추출(createResult);

        SubscriptionUpdateRequest updateRequest = new SubscriptionUpdateRequest(
                null, null, null, null, null, 5, null
        );

        mockMvc.perform(put("/api/subscriptions/" + id)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 구독_목록_조회_성공() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        구독_등록(session, request);

        mockMvc.perform(get("/api/subscriptions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].serviceName").value("Netflix"));
    }

    @Test
    void 구독_상세_조회_성공() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        MvcResult createResult = 구독_등록(session, request);
        long id = 구독_ID_추출(createResult);

        mockMvc.perform(get("/api/subscriptions/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceName").value("Netflix"));
    }

    @Test
    void 구독_상세_조회_없는_구독_404() throws Exception {
        mockMvc.perform(get("/api/subscriptions/99999").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 구독_수정_성공() throws Exception {
        SubscriptionCreateRequest createRequest = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        MvcResult createResult = 구독_등록(session, createRequest);
        long id = 구독_ID_추출(createResult);

        SubscriptionUpdateRequest updateRequest = new SubscriptionUpdateRequest(
                "Netflix Premium", SubscriptionCategory.OTT, 19000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );

        mockMvc.perform(put("/api/subscriptions/" + id)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceName").value("Netflix Premium"))
                .andExpect(jsonPath("$.data.price").value(19000));
    }

    @Test
    void 구독_삭제_성공() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        MvcResult createResult = 구독_등록(session, request);
        long id = 구독_ID_추출(createResult);

        mockMvc.perform(delete("/api/subscriptions/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/subscriptions/" + id).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void 구독_상태_변경_성공() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        MvcResult createResult = 구독_등록(session, request);
        long id = 구독_ID_추출(createResult);

        SubscriptionStatusUpdateRequest statusRequest = new SubscriptionStatusUpdateRequest(SubscriptionStatus.PAUSED);

        mockMvc.perform(put("/api/subscriptions/" + id + "/status")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    @Test
    void 다른_사용자_구독_접근_403() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD
        );
        MvcResult createResult = 구독_등록(session, request);
        long id = 구독_ID_추출(createResult);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SignupRequest("타인", "other@example.com", "Password1!"))));
        MvcResult otherLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("other@example.com", "Password1!"))))
                .andReturn();
        MockHttpSession otherSession = (MockHttpSession) otherLogin.getRequest().getSession();

        mockMvc.perform(get("/api/subscriptions/" + id).session(otherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 서비스명_중복_확인_중복있음() throws Exception {
        구독_등록(session, new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD));

        mockMvc.perform(get("/api/subscriptions/check-name")
                        .param("name", "netflix")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void 서비스명_중복_확인_중복없음() throws Exception {
        mockMvc.perform(get("/api/subscriptions/check-name")
                        .param("name", "Spotify")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void 서비스명_중복_확인_삭제된_구독은_중복아님() throws Exception {
        MvcResult createResult = 구독_등록(session, new SubscriptionCreateRequest(
                "Netflix", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, 15, null, PaymentMethod.CARD));
        long id = 구독_ID_추출(createResult);

        mockMvc.perform(delete("/api/subscriptions/" + id).session(session));

        mockMvc.perform(get("/api/subscriptions/check-name")
                        .param("name", "Netflix")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void 미인증_구독_목록_조회_401() throws Exception {
        mockMvc.perform(get("/api/subscriptions"))
                .andExpect(status().isUnauthorized());
    }
}
