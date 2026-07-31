package com.scrumble.gudocs.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;
    @Autowired
    private PushRegistrationRepository pushRegistrationRepository;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, "테스터", "push@example.com");
    }

    private String body(String fid) throws Exception {
        return objectMapper.writeValueAsString(new PushRegistrationRequest(fid, PushPlatform.WEB, "Chrome"));
    }

    @Test
    void 신규_등록_201() throws Exception {
        mockMvc.perform(post("/api/push-registrations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fid-ctrl-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.platform").value("WEB"));
    }

    @Test
    void fid_공백이면_400() throws Exception {
        mockMvc.perform(post("/api/push-registrations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fid가_255자_초과면_400() throws Exception {
        mockMvc.perform(post("/api/push-registrations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("a".repeat(256))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 미인증_요청_401() throws Exception {
        mockMvc.perform(post("/api/push-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fid-ctrl-2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 등록_해제_성공() throws Exception {
        // 먼저 등록
        var result = mockMvc.perform(post("/api/push-registrations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fid-ctrl-3")))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("registrationId").asLong();

        mockMvc.perform(delete("/api/push-registrations/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.assertj.core.api.Assertions.assertThat(
                pushRegistrationRepository.findById(id).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void 다른_사용자_등록_해제_차단_403() throws Exception {
        // owner 세션으로 등록
        var result = mockMvc.perform(post("/api/push-registrations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fid-ctrl-4")))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("registrationId").asLong();

        // 다른 사용자 세션
        MockHttpSession attacker = TestSessions.loginNew(
                userRepository, socialAccountRepository, "공격자", "attacker@example.com");

        mockMvc.perform(delete("/api/push-registrations/{id}", id).session(attacker))
                .andExpect(status().isForbidden());
    }
}
