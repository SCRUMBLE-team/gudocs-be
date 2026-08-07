package com.scrumble.gudocs.subscriptions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, "테스터", "sub@example.com");
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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
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
    void 구독_등록_필수값_누락_400() throws Exception {
        String body = "{\"category\":\"OTT\",\"price\":17000,\"billingCycle\":\"MONTHLY\",\"firstBillingDate\":\"2025-01-15\"}";

        mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 구독_수정_필수값_누락_400() throws Exception {
        SubscriptionCreateRequest createRequest = new SubscriptionCreateRequest(
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
        );
        MvcResult createResult = 구독_등록(session, createRequest);
        long id = 구독_ID_추출(createResult);

        SubscriptionUpdateRequest updateRequest = new SubscriptionUpdateRequest(
                null, null, null, null, null, null
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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
        );
        MvcResult createResult = 구독_등록(session, createRequest);
        long id = 구독_ID_추출(createResult);

        SubscriptionUpdateRequest updateRequest = new SubscriptionUpdateRequest(
                "Netflix Premium", null, SubscriptionCategory.OTT, 19000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)
        );
        MvcResult createResult = 구독_등록(session, request);
        long id = 구독_ID_추출(createResult);

        MockHttpSession otherSession = TestSessions.loginNew(userRepository, socialAccountRepository, "타인", "other@example.com");

        mockMvc.perform(get("/api/subscriptions/" + id).session(otherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 서비스명_중복_확인_중복있음() throws Exception {
        구독_등록(session, new SubscriptionCreateRequest(
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)));

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
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)));
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

    @Test
    void 서비스명_중복_확인_빈문자열_400() throws Exception {
        mockMvc.perform(get("/api/subscriptions/check-name")
                        .param("name", "   ")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 서비스명_중복_확인_앞뒤공백_트리밍() throws Exception {
        구독_등록(session, new SubscriptionCreateRequest(
                "Netflix", null, SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2025, 1, 15)));

        mockMvc.perform(get("/api/subscriptions/check-name")
                        .param("name", "  Netflix  ")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void 카탈로그_조회_성공() throws Exception {
        mockMvc.perform(get("/api/subscriptions/catalog").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pricesCheckedOn").isNotEmpty())
                .andExpect(jsonPath("$.data.services[?(@.code == 'NETFLIX')].name").value("넷플릭스"))
                .andExpect(jsonPath("$.data.services[?(@.code == 'NETFLIX')].category").value("OTT"))
                .andExpect(jsonPath("$.data.services[?(@.code == 'NETFLIX')].discontinued").value(false))
                .andExpect(jsonPath("$.data.services[?(@.code == 'NETFLIX')].plans[0].price").value(7000))
                .andExpect(jsonPath("$.data.services[?(@.code == 'CLOVA_X')].discontinued").value(true))
                // aliases 는 OCR 매칭 전용이라 응답에 노출하지 않는다.
                .andExpect(jsonPath("$.data.services[0].aliases").doesNotExist());
    }

    @Test
    void 카탈로그_조회_미인증_401() throws Exception {
        mockMvc.perform(get("/api/subscriptions/catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 구독_등록시_serviceCode를_저장하고_응답한다() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "넷플릭스", "NETFLIX", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2026, 1, 15));

        MvcResult result = 구독_등록(session, request);
        long id = 구독_ID_추출(result);

        // 표시 이름이 아니라 code 로 로고를 찾을 수 있어야 한다.
        mockMvc.perform(get("/api/subscriptions/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceCode").value("NETFLIX"))
                .andExpect(jsonPath("$.data.serviceName").value("넷플릭스"));
    }

    @Test
    void 직접_입력한_서비스는_serviceCode가_null이다() throws Exception {
        MvcResult result = 구독_등록(session, new SubscriptionCreateRequest(
                "동네 헬스장", null, SubscriptionCategory.ETC, 50000L,
                BillingCycle.MONTHLY, LocalDate.of(2026, 1, 15)));

        mockMvc.perform(get("/api/subscriptions/" + 구독_ID_추출(result) + "").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceCode").doesNotExist());
    }

    @Test
    void 카탈로그에_없는_serviceCode는_400() throws Exception {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(
                "넷플릭스", "NOT_A_REAL_CODE", SubscriptionCategory.OTT, 17000L,
                BillingCycle.MONTHLY, LocalDate.of(2026, 1, 15));

        mockMvc.perform(post("/api/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
