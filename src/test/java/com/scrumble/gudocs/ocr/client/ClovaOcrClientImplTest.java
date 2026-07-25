package com.scrumble.gudocs.ocr.client;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClovaOcrClientImplTest {

    @Test
    void invokeUrl이_비어있으면_BusinessException으로_변환된다() {
        ClovaOcrClientImpl client = new ClovaOcrClientImpl(RestClient.builder(), "", "");

        assertThatThrownBy(() -> client.extractText(new byte[]{1}, "jpg"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    void 필드를_lineBreak_기준으로_이어붙여_평문_텍스트로_만든다() {
        ClovaOcrResponse.Field field1 = new ClovaOcrResponse.Field("넷플릭스", true);
        ClovaOcrResponse.Field field2 = new ClovaOcrResponse.Field("17,000원", false);
        ClovaOcrResponse.Field field3 = new ClovaOcrResponse.Field("결제완료", true);
        ClovaOcrResponse.Image image = new ClovaOcrResponse.Image("SUCCESS", List.of(field1, field2, field3));
        ClovaOcrResponse response = new ClovaOcrResponse(List.of(image));

        String text = ClovaOcrClientImpl.toPlainText(response);

        assertThat(text).isEqualTo("넷플릭스\n17,000원 결제완료\n");
    }

    @Test
    void images가_비어있으면_빈_문자열을_반환한다() {
        ClovaOcrResponse response = new ClovaOcrResponse(List.of());

        assertThat(ClovaOcrClientImpl.toPlainText(response)).isEmpty();
    }
}
