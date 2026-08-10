package com.scrumble.gudocs.subscriptions.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.subscriptions.dto.request.SavingsSelectionRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.response.CatalogResponse;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import com.scrumble.gudocs.global.security.CurrentUserId;

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
    ResponseEntity<ApiResponse<SubscriptionResponse>> create(@CurrentUserId Long userId,
            @Valid @RequestBody SubscriptionCreateRequest request);

    @Operation(summary = "구독 목록 조회", description = "현재 사용자의 구독 목록을 조회합니다. (삭제된 항목 제외)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getAll(@CurrentUserId Long userId);

    @Operation(summary = "구독 상세 조회", description = "특정 구독 서비스의 상세 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<SubscriptionResponse>> getOne(@CurrentUserId Long userId,
            Long subscriptionId);

    @Operation(summary = "구독 수정", description = "구독 서비스 정보를 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<SubscriptionResponse>> update(@CurrentUserId Long userId,
            Long subscriptionId,
            @Valid @RequestBody SubscriptionUpdateRequest request);

    @Operation(summary = "구독 삭제", description = "구독 서비스를 삭제합니다. (soft delete)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<Void>> delete(@CurrentUserId Long userId,
            Long subscriptionId);

    @Operation(summary = "서비스 중복 확인",
            description = "이미 등록한 서비스인지 확인합니다. (삭제되지 않은 구독 대상, 경고 용도) "
                    + "code를 함께 보내면 서비스 코드로 판정하고, 없으면 서비스명을 대소문자 무시하고 비교합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<Boolean>> checkDuplicateName(@CurrentUserId Long userId,
            @Parameter(description = "확인할 서비스명 (카탈로그에서 고른 서비스면 카탈로그의 name)",
                    example = "넷플릭스", required = true)
            @NotBlank @Size(max = 100) String name,
            @Parameter(description = "카탈로그 서비스 코드. 있으면 이름 대신 이 값으로 판정한다. 직접 입력한 서비스면 생략.",
                    example = "NETFLIX")
            @Size(max = 64) String code);

    @Operation(summary = "구독 서비스 카탈로그 조회",
            description = "구독 등록 화면에서 쓰는 서비스·요금제 목록을 조회합니다. "
                    + "요금은 서버에 고정된 참고값이며 사용자가 수정할 수 있습니다. "
                    + "원화 정가를 확인하지 못한 서비스는 요금제가 빈 배열로 내려갑니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<CatalogResponse>> getCatalog();

    @Operation(summary = "절약 후보 구독 조회",
            description = "절약하기 화면에서 해지 후보로 체크해 둔 구독 목록을 조회합니다. "
                    + "체크한 시각 최신순입니다. 알림을 받고 화면에 들어왔을 때 이 API로 최신 목록을 받습니다 "
                    + "(알림 payload에는 구독 id를 싣지 않습니다 — 발송 시점 스냅샷이라 그 사이 변경과 어긋납니다).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getSavingsSelection(@CurrentUserId Long userId);

    @Operation(summary = "절약 후보 구독 저장",
            description = "절약하기 화면에서 체크한 구독 id 목록을 저장합니다. 현재 선택을 요청 목록으로 "
                    + "통째로 대체하므로 빈 배열이면 전체 해제이고, 같은 요청을 반복해도 결과가 같습니다. "
                    + "본인 구독이 아니거나 없는 id가 섞여 있으면 아무것도 저장하지 않고 404를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<List<SubscriptionResponse>>> replaceSavingsSelection(@CurrentUserId Long userId,
            @Valid @RequestBody SavingsSelectionRequest request);

    @Operation(summary = "구독 상태 변경", description = "구독 상태를 ACTIVE 또는 PAUSED로 변경합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "구독 없음")
    })
    ResponseEntity<ApiResponse<SubscriptionResponse>> updateStatus(@CurrentUserId Long userId,
            Long subscriptionId,
            @Valid @RequestBody SubscriptionStatusUpdateRequest request);
}
