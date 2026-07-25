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
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final ClovaOcrClient clovaOcrClient;

    public OcrSubscriptionResult scanSubscription(MultipartFile image) {
        validate(image);
        String format = "image/png".equalsIgnoreCase(image.getContentType()) ? "png" : "jpg";
        String text = clovaOcrClient.extractText(readBytes(image), format);
        return SubscriptionTextParser.parse(text, LocalDate.now());
    }

    private void validate(MultipartFile image) {
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

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }
}
