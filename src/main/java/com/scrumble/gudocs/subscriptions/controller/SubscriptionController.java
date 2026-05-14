package com.scrumble.gudocs.subscriptions.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import com.scrumble.gudocs.subscriptions.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionResponse response = subscriptionService.create(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("구독 서비스 등록 성공", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<SubscriptionResponse> response = subscriptionService.getAll(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 목록 조회 성공", response));
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getOne(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long subscriptionId) {
        SubscriptionResponse response = subscriptionService.getOne(userDetails.getUsername(), subscriptionId);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 상세 조회 성공", response));
    }

    @PutMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        SubscriptionResponse response = subscriptionService.update(userDetails.getUsername(), subscriptionId, request);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 수정 성공", response));
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long subscriptionId) {
        subscriptionService.delete(userDetails.getUsername(), subscriptionId);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 삭제 성공"));
    }

    @PutMapping("/{subscriptionId}/status")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody SubscriptionStatusUpdateRequest request) {
        SubscriptionResponse response = subscriptionService.updateStatus(
                userDetails.getUsername(), subscriptionId, request);
        return ResponseEntity.ok(ApiResponse.success("구독 상태 변경 성공", response));
    }
}
