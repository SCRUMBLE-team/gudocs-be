package com.scrumble.gudocs.subscriptions.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.dto.response.CatalogResponse;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import com.scrumble.gudocs.subscriptions.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.scrumble.gudocs.global.security.CurrentUserId;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Validated
public class SubscriptionController implements SubscriptionApi {

    private final SubscriptionService subscriptionService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionResponse response = subscriptionService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("구독 서비스 등록 성공", response));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getAll(
            @CurrentUserId Long userId) {
        List<SubscriptionResponse> response = subscriptionService.getAll(userId);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 목록 조회 성공", response));
    }

    @Override
    @GetMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getOne(
            @CurrentUserId Long userId,
            @PathVariable Long subscriptionId) {
        SubscriptionResponse response = subscriptionService.getOne(userId, subscriptionId);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 상세 조회 성공", response));
    }

    @Override
    @PutMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> update(
            @CurrentUserId Long userId,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        SubscriptionResponse response = subscriptionService.update(userId, subscriptionId, request);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 수정 성공", response));
    }

    @Override
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUserId Long userId,
            @PathVariable Long subscriptionId) {
        subscriptionService.delete(userId, subscriptionId);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 삭제 성공"));
    }

    @Override
    @GetMapping("/check-name")
    public ResponseEntity<ApiResponse<Boolean>> checkDuplicateName(
            @CurrentUserId Long userId,
            @RequestParam String name,
            @RequestParam(required = false) String code) {
        boolean isDuplicate = subscriptionService.isDuplicateService(userId, name, code);
        return ResponseEntity.ok(ApiResponse.success("서비스 중복 확인", isDuplicate));
    }

    @Override
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<CatalogResponse>> getCatalog() {
        // 정적 데이터라 서비스 계층을 거치지 않는다.
        CatalogResponse response = CatalogResponse.from(ServiceCatalog.services());
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 카탈로그 조회 성공", response));
    }

    @Override
    @PutMapping("/{subscriptionId}/status")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> updateStatus(
            @CurrentUserId Long userId,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody SubscriptionStatusUpdateRequest request) {
        SubscriptionResponse response = subscriptionService.updateStatus(
                userId, subscriptionId, request);
        return ResponseEntity.ok(ApiResponse.success("구독 상태 변경 성공", response));
    }
}
