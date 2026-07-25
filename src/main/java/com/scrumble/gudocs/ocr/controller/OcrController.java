package com.scrumble.gudocs.ocr.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.ocr.service.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController implements OcrApi {

    private final OcrService ocrService;

    @Override
    @PostMapping(value = "/subscriptions/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<OcrSubscriptionResult>> scan(
            @CurrentUserId Long userId, @RequestPart("image") MultipartFile image) {
        OcrSubscriptionResult result = ocrService.scanSubscription(image);
        return ResponseEntity.ok(ApiResponse.success("구독 정보 인식에 성공했습니다.", result));
    }
}
