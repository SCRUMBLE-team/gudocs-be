package com.scrumble.gudocs.subscriptions.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

@Tag(name = "Subscriptions", description = "구독 서비스 CRUD API")
@SecurityRequirement(name = "cookieAuth")
public interface SubscriptionApi {

    @Operation(summary = "구독 등록", description = "새로운 구독 서비스를 등록합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<SubscriptionResponse>> create(@Parameter(hidden = true) UserDetails userDetails,
            SubscriptionCreateRequest request);

    @Operation(summary = "구독 목록 조회", description = "현재 사용자의 구독 목록을 조회합니다. (삭제된 항목 제외)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getAll(@Parameter(hidden = true) UserDetails userDetails);

    @Operation(summary = "구독 상세 조회", description = "특정 구독 서비스의 상세 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<SubscriptionResponse>> getOne(@Parameter(hidden = true) UserDetails userDetails,
            Long subscriptionId);

    @Operation(summary = "구독 수정", description = "구독 서비스 정보를 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<SubscriptionResponse>> update(@Parameter(hidden = true) UserDetails userDetails,
            Long subscriptionId,
            SubscriptionUpdateRequest request);

    @Operation(summary = "구독 삭제", description = "구독 서비스를 삭제합니다. (soft delete)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<Void>> delete(@Parameter(hidden = true) UserDetails userDetails,
            Long subscriptionId);

    @Operation(summary = "구독 상태 변경", description = "구독 상태를 ACTIVE 또는 PAUSED로 변경합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<SubscriptionResponse>> updateStatus(@Parameter(hidden = true) UserDetails userDetails,
            Long subscriptionId,
            SubscriptionStatusUpdateRequest request);
}
