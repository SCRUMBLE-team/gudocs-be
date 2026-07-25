package com.scrumble.gudocs.ocr.client;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class ClovaOcrClientImpl implements ClovaOcrClient {

    private static final Logger log = LoggerFactory.getLogger(ClovaOcrClientImpl.class);

    private final RestClient restClient = RestClient.create();

    @Value("${app.ocr.clova.invoke-url}")
    private String invokeUrl;

    @Value("${app.ocr.clova.secret-key}")
    private String secretKey;

    @Override
    public String extractText(byte[] imageBytes, String imageFormat) {
        ClovaOcrRequest request = new ClovaOcrRequest(
                "V2",
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                List.of(new ClovaOcrRequest.Image(imageFormat, "image", Base64.getEncoder().encodeToString(imageBytes)))
        );

        try {
            ClovaOcrResponse response = restClient.post()
                    .uri(invokeUrl)
                    .header("X-OCR-SECRET", secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ClovaOcrResponse.class);

            return toPlainText(response);
        } catch (RestClientException e) {
            log.error("CLOVA OCR API 호출 실패", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    static String toPlainText(ClovaOcrResponse response) {
        if (response == null || response.images() == null || response.images().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ClovaOcrResponse.Image image : response.images()) {
            if (image.fields() == null) {
                continue;
            }
            for (ClovaOcrResponse.Field field : image.fields()) {
                sb.append(field.inferText());
                sb.append(Boolean.TRUE.equals(field.lineBreak()) ? "\n" : " ");
            }
        }
        return sb.toString();
    }
}
