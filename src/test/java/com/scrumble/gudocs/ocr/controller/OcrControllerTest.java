package com.scrumble.gudocs.ocr.controller;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.ocr.client.ClovaOcrClient;
import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @MockitoBean
    private ClovaOcrClient clovaOcrClient;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = TestSessions.loginNew(userRepository, socialAccountRepository, "테스터", "ocr@example.com");
    }

    @Test
    void 이미지를_업로드하면_인식된_구독_정보를_반환한다() throws Exception {
        given(clovaOcrClient.extractText(any(byte[].class), any(String.class)))
                .willReturn("넷플릭스 17,000원 2026.07.15 카드");
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(image).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceName").value("넷플릭스"))
                .andExpect(jsonPath("$.data.category").value("OTT"))
                .andExpect(jsonPath("$.data.price").value(17000));
    }

    @Test
    void 이미지가_아닌_파일이면_400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(file).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 미인증_401() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(image))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CLOVA_호출_실패시_502() throws Exception {
        given(clovaOcrClient.extractText(any(byte[].class), any(String.class)))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/ocr/subscriptions/scan").file(image).session(session))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }
}
