package com.scrumble.gudocs.ocr.client;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    private final RestClient restClient;
    private final String invokeUrl;
    private final String secretKey;

    public ClovaOcrClientImpl(
            RestClient.Builder restClientBuilder,
            @Value("${app.ocr.clova.invoke-url}") String invokeUrl,
            @Value("${app.ocr.clova.secret-key}") String secretKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.invokeUrl = invokeUrl;
        this.secretKey = secretKey;
    }

    @Override
    public String extractText(byte[] imageBytes, String imageFormat) {
        ClovaOcrRequest request = new ClovaOcrRequest(
                "V2",
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                List.of(new ClovaOcrRequest.Image(imageFormat, "image", Base64.getEncoder().encodeToString(imageBytes)))
        );

        ClovaOcrResponse response;
        try {
            response = restClient.post()
                    .uri(invokeUrl)
                    .header("X-OCR-SECRET", secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ClovaOcrResponse.class);
        } catch (RestClientException | IllegalArgumentException e) {
            // IllegalArgumentException: invokeUrl이 비어있거나 형식이 잘못돼 URI 생성 자체가 실패하는 경우(RestClientException으로 래핑되지 않음)
            log.error("CLOVA OCR API 호출 실패", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }

        return toPlainText(response);
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
