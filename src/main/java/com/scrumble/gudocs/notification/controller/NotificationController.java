package com.scrumble.gudocs.notification.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.notification.dto.response.UpcomingNotification;
import com.scrumble.gudocs.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.scrumble.gudocs.global.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @Override
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<UpcomingNotification>>> getUpcoming(
            @CurrentUserId Long userId) {
        List<UpcomingNotification> response = notificationService.findUpcoming(userId);
        return ResponseEntity.ok(ApiResponse.success("다가오는 결제 알림 조회 성공", response));
    }
}
