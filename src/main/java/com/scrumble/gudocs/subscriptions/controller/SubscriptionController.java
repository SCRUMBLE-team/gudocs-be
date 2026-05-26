package com.scrumble.gudocs.subscriptions.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import com.scrumble.gudocs.subscriptions.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Subscriptions", description = "구독 서비스 CRUD API")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "구독 등록", description = "새로운 구독 서비스를 등록합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionResponse response = subscriptionService.create(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("구독 서비스 등록 성공", response));
    }

    @Operation(summary = "구독 목록 조회", description = "현재 사용자의 구독 목록을 조회합니다. (삭제된 항목 제외)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<SubscriptionResponse> response = subscriptionService.getAll(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 목록 조회 성공", response));
    }

    @Operation(summary = "구독 상세 조회", description = "특정 구독 서비스의 상세 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    @GetMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getOne(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long subscriptionId) {
        SubscriptionResponse response = subscriptionService.getOne(userDetails.getUsername(), subscriptionId);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 상세 조회 성공", response));
    }

    @Operation(summary = "구독 수정", description = "구독 서비스 정보를 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    @PutMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long subscriptionId,
            @Valid @RequestBody SubscriptionUpdateRequest request) {
        SubscriptionResponse response = subscriptionService.update(userDetails.getUsername(), subscriptionId, request);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 수정 성공", response));
    }

    @Operation(summary = "구독 삭제", description = "구독 서비스를 삭제합니다. (soft delete)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long subscriptionId) {
        subscriptionService.delete(userDetails.getUsername(), subscriptionId);
        return ResponseEntity.ok(ApiResponse.success("구독 서비스 삭제 성공"));
    }

    @Operation(summary = "구독 상태 변경", description = "구독 상태를 ACTIVE 또는 PAUSED로 변경합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
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
