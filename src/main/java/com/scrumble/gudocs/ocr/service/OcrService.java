package com.scrumble.gudocs.ocr.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.ocr.client.ClovaOcrClient;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.ocr.parser.SubscriptionTextParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OcrService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    // 방어적 이중 체크: 실제 HTTP 요청에서는 서블릿 컨테이너가 spring.servlet.multipart.max-file-size
    // (application.yaml, test/resources/application.yaml)를 먼저 적용해 초과 요청을 거부하므로 이 분기는
    // 보통 도달하지 않는다. 값을 바꿀 때는 두 application.yaml의 max-file-size와 반드시 동기화할 것.
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};

    private final ClovaOcrClient clovaOcrClient;

    public OcrSubscriptionResult scanSubscription(MultipartFile image) {
        validateMetadata(image);
        byte[] bytes = readBytes(image);
        // Content-Type 헤더는 클라이언트가 임의로 지정 가능해 위장될 수 있으므로,
        // 실제 파일 시작 바이트(매직 넘버)로 JPEG/PNG 여부를 다시 검증한다.
        validateMagicBytes(bytes);

        String format = "image/png".equalsIgnoreCase(image.getContentType()) ? "png" : "jpg";
        String text = clovaOcrClient.extractText(bytes, format);
        return SubscriptionTextParser.parse(text, LocalDate.now());
    }

    private void validateMetadata(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private void validateMagicBytes(byte[] bytes) {
        if (!startsWith(bytes, JPEG_MAGIC) && !startsWith(bytes, PNG_MAGIC)) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }
}
