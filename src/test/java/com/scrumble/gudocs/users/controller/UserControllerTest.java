package com.scrumble.gudocs.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.users.dto.UserNameUpdateRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    private MockHttpSession session;

    private static final String EMAIL = "user@example.com";
    private static final String NAME = "테스터";

    @BeforeEach
    void setUp() {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, NAME, EMAIL);
    }

    // ── GET /api/users/me 는 제거됨 (내 정보 조회는 GET /api/auth/me로 일원화) ──

    @Test
    void 내_정보_조회_GET_API_제거_405() throws Exception {
        // /api/users/me 경로에는 DELETE(회원 탈퇴)만 남아 GET은 405를 반환한다.
        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isMethodNotAllowed());
    }

    // ── PUT /api/users/me/name ─────────────────────────────────

    @Test
    void 이름_수정_성공() throws Exception {
        UserNameUpdateRequest request = new UserNameUpdateRequest("새이름");

        mockMvc.perform(put("/api/users/me/name")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("이름이 수정되었습니다."))
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    void 이름_수정_빈값_400() throws Exception {
        UserNameUpdateRequest request = new UserNameUpdateRequest("");

        mockMvc.perform(put("/api/users/me/name")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 이름_수정_공백만_있는_값_400() throws Exception {
        UserNameUpdateRequest request = new UserNameUpdateRequest("   ");

        mockMvc.perform(put("/api/users/me/name")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 이름_수정_미인증_401() throws Exception {
        UserNameUpdateRequest request = new UserNameUpdateRequest("새이름");

        mockMvc.perform(put("/api/users/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/users/me ───────────────────────────────────

    @Test
    void 회원_탈퇴_성공() throws Exception {
        mockMvc.perform(delete("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));
    }

    @Test
    void 회원_탈퇴_후_세션_무효화() throws Exception {
        mockMvc.perform(delete("/api/users/me").session(session));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_탈퇴_미인증_401() throws Exception {
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
