package com.scrumble.gudocs.ocr.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.ocr.client.ClovaOcrClient;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private ClovaOcrClient clovaOcrClient;

    @InjectMocks
    private OcrService ocrService;

    @Test
    void 정상_이미지는_OCR_결과를_파싱해서_반환한다() {
        MultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(clovaOcrClient.extractText(any(byte[].class), eq("jpg"))).willReturn("넷플릭스 17,000원 2026.07.15 카드");

        OcrSubscriptionResult result = ocrService.scanSubscription(image);

        assertThat(result.serviceName()).isEqualTo("넷플릭스");
        assertThat(result.price()).isEqualTo(17000L);
    }

    @Test
    void 파일이_비어있으면_예외() {
        MultipartFile empty = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> ocrService.scanSubscription(empty))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 이미지가_아닌_파일이면_예외() {
        MultipartFile pdf = new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> ocrService.scanSubscription(pdf))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void MB초과_파일이면_예외() {
        MultipartFile huge = new MockMultipartFile("image", "huge.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);

        assertThatThrownBy(() -> ocrService.scanSubscription(huge))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }
}
