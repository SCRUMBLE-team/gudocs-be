package com.scrumble.gudocs.ocr.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "OCR", description = "CLOVA OCR 기반 구독 정보 인식 API")
@SecurityRequirement(name = "cookieAuth")
public interface OcrApi {

    @Operation(summary = "구독 결제 이미지 OCR 인식",
            description = "결제 알림 캡처/영수증/구독 화면 이미지를 업로드하면 CLOVA OCR로 텍스트를 추출하고 "
                    + "구독 등록에 필요한 필드를 best-effort로 파싱해 반환합니다. 인식 실패 필드는 null입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인식 성공(일부 필드 null 가능)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미지 파일이 없거나 형식/용량이 유효하지 않음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "CLOVA OCR API 호출 실패")
    })
    ResponseEntity<ApiResponse<OcrSubscriptionResult>> scan(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "구독 결제 관련 이미지 (jpg/png, 최대 10MB)") MultipartFile image);
}
