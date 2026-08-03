package com.scrumble.gudocs.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.entity.PushPlatform;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushTestControllerTest {

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
    void setUp() {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, "테스터", "pushtest@example.com");
    }

    private void register(String fid) throws Exception {
        mockMvc.perform(post("/api/push-registrations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PushRegistrationRequest(fid, PushPlatform.WEB, "Chrome"))))
                .andExpect(status().isCreated());
    }

    @Test
    void 등록된_기기로_테스트_발송_성공() throws Exception {
        register("fid-test-1");

        // test 프로파일은 Firebase 비활성 → NoopPushSender가 SUCCESS 반환
        mockMvc.perform(post("/api/push-registrations/test").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.senderType").value("NoopPushSender"))
                .andExpect(jsonPath("$.data.deviceCount").value(1))
                .andExpect(jsonPath("$.data.results[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.results[0].fid").value("fid-te***"));
    }

    @Test
    void 등록된_기기가_없으면_deviceCount_0() throws Exception {
        mockMvc.perform(post("/api/push-registrations/test").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceCount").value(0));
    }

    @Test
    void 미인증_요청_401() throws Exception {
        mockMvc.perform(post("/api/push-registrations/test"))
                .andExpect(status().isUnauthorized());
    }
}
