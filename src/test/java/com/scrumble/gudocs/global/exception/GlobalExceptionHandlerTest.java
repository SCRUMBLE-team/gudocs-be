package com.scrumble.gudocs.global.exception;

import com.scrumble.gudocs.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void 업로드_용량_초과_예외는_INVALID_IMAGE_FILE_응답으로_변환된다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(0));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE.getStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE.getMessage());
    }
}
