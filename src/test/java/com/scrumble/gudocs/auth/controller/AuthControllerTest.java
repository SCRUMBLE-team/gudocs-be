package com.scrumble.gudocs.auth.controller;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    private MockHttpSession session;

    private static final String EMAIL = "auth@example.com";
    private static final String NAME = "인증테스터";

    @BeforeEach
    void setUp() {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, NAME, EMAIL);
    }

    @Test
    void 내_정보_조회_유지() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value(NAME))
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    void 미인증_요청은_401_ApiResponse_JSON을_반환() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
