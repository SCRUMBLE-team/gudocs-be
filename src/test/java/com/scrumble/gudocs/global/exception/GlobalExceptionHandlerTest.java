package com.scrumble.gudocs.global.exception;

import com.scrumble.gudocs.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

    @Test
    void 처리되지_않은_예외는_500_ApiResponse로_변환된다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleException(new NullPointerException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }
}
