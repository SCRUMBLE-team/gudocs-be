package com.scrumble.gudocs.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.auth.dto.LoginRequest;
import com.scrumble.gudocs.auth.dto.SignupRequest;
import com.scrumble.gudocs.users.dto.UserDeleteRequest;
import com.scrumble.gudocs.users.dto.UserNameUpdateRequest;
import com.scrumble.gudocs.users.dto.UserPasswordUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession session;

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Password1!";
    private static final String NAME = "테스터";

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SignupRequest(NAME, EMAIL, PASSWORD))))
                .andExpect(status().is2xxSuccessful());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
    }

    // ── GET /api/users/me ──────────────────────────────────────

    @Test
    void 내_정보_조회_성공() throws Exception {
        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("내 정보 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value(NAME))
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    void 내_정보_조회_미인증_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
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

    // ── PUT /api/users/me/password ─────────────────────────────

    @Test
    void 비밀번호_수정_성공() throws Exception {
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(PASSWORD, "NewPassword1!");

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("비밀번호가 수정되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 비밀번호_수정_현재_비밀번호_불일치_400() throws Exception {
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest("WrongPass1!", "NewPassword1!");

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 비밀번호_수정_새_비밀번호가_현재와_동일_400() throws Exception {
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(PASSWORD, PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 비밀번호_수정_새_비밀번호_누락_400() throws Exception {
        String body = "{\"currentPassword\":\"" + PASSWORD + "\"}";

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 비밀번호_수정_새_비밀번호_짧음_400() throws Exception {
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(PASSWORD, "short");

        mockMvc.perform(put("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 비밀번호_수정_미인증_401() throws Exception {
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest(PASSWORD, "NewPassword1!");

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/users/me ───────────────────────────────────

    @Test
    void 회원_탈퇴_성공() throws Exception {
        UserDeleteRequest request = new UserDeleteRequest(PASSWORD);

        mockMvc.perform(delete("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."));
    }

    @Test
    void 회원_탈퇴_후_세션_무효화() throws Exception {
        UserDeleteRequest request = new UserDeleteRequest(PASSWORD);

        mockMvc.perform(delete("/api/users/me")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_탈퇴_비밀번호_불일치_400() throws Exception {
        UserDeleteRequest request = new UserDeleteRequest("WrongPass1!");

        mockMvc.perform(delete("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 회원_탈퇴_비밀번호_누락_400() throws Exception {
        String body = "{}";

        mockMvc.perform(delete("/api/users/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 회원_탈퇴_미인증_401() throws Exception {
        UserDeleteRequest request = new UserDeleteRequest(PASSWORD);

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
