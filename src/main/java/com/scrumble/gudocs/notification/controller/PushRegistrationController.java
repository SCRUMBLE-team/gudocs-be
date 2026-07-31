package com.scrumble.gudocs.notification.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.dto.response.PushRegistrationResponse;
import com.scrumble.gudocs.notification.service.PushRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push-registrations")
@RequiredArgsConstructor
public class PushRegistrationController implements PushRegistrationApi {

    private final PushRegistrationService pushRegistrationService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<PushRegistrationResponse>> register(
            @CurrentUserId Long userId,
            @Valid @RequestBody PushRegistrationRequest request) {
        PushRegistrationResponse response = pushRegistrationService.register(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("푸시 기기 등록 성공", response));
    }

    @Override
    @DeleteMapping("/{registrationId}")
    public ResponseEntity<ApiResponse<Void>> unregister(
            @CurrentUserId Long userId,
            @PathVariable Long registrationId) {
        pushRegistrationService.unregister(userId, registrationId);
        return ResponseEntity.ok(ApiResponse.success("푸시 기기 등록이 해제되었습니다."));
    }
}
