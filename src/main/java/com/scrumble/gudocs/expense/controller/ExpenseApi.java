package com.scrumble.gudocs.expense.controller;

import com.scrumble.gudocs.expense.dto.response.CategoryExpenseResponse;
import com.scrumble.gudocs.expense.dto.response.ExpenseTrendResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseDetailResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseResponse;
import com.scrumble.gudocs.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import com.scrumble.gudocs.global.security.CurrentUserId;

/**
 * 지출 분석 API 문서. 금액의 출처와 진행 중인 달의 처리가 화면 숫자를 좌우하므로 태그 설명에 함께 적는다
 * (엔드포인트별 설명에 같은 문단을 네 번 반복하지 않기 위해).
 */
@Tag(name = "Expenses", description = """
        지출 분석 API.

        **금액 출처**: 모든 금액은 `billing_records`(청구 예정일 도래 시 저장한 구독 등록정보 스냅샷)에서 나옵니다. \
        카드·은행의 실제 승인 내역이 아니며, 청구 후 구독 가격·카테고리를 수정해도 과거 금액은 바뀌지 않습니다. \
        일시정지한 기간은 기록이 생기지 않아 그 달 지출이 0입니다.

        **두 가지 금액**: `totalAmount`류는 *월평균 부담*(청구액을 커버 기간에 분산 — 연간 120,000원은 12개월간 10,000원씩)이고, \
        `actualAmount`는 *그 달에 기록된 청구액*(연간 구독은 청구월에 전액)입니다.

        **진행 중인 달**: 이번 달에 한해 아직 청구일이 오지 않은 활성 구독의 예정 청구액이 부담에 포함됩니다. \
        (15일 결제 구독이 14일까지 0원으로 보이지 않도록) 지난 달·미래 달에는 적용되지 않고, `actualAmount`에도 포함되지 않습니다.
        """)
@SecurityRequirement(name = "cookieAuth")
public interface ExpenseApi {

    @Operation(summary = "월별 지출 분석", description = "특정 연월의 구독 총 지출 및 전월 대비 변화율을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<MonthlyExpenseResponse>> getMonthlyExpense(
            @CurrentUserId Long userId,
            @Parameter(description = "연도 (예: 2025)") int year,
            @Parameter(description = "월 (1~12)") int month);

    @Operation(summary = "카테고리별 지출 분석", description = "특정 연월의 카테고리별 구독 지출을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<CategoryExpenseResponse>> getCategoryExpense(
            @CurrentUserId Long userId,
            @Parameter(description = "연도 (예: 2025)") int year,
            @Parameter(description = "월 (1~12)") int month);

    @Operation(summary = "최근 6개월 지출 추이", description = "기준 월을 포함한 최근 6개월간의 월별 지출 추이를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<ExpenseTrendResponse>> getExpenseTrend(
            @CurrentUserId Long userId,
            @Parameter(description = "기준 연도 (예: 2025)") int baseYear,
            @Parameter(description = "기준 월 (1~12)") int baseMonth);

    @Operation(summary = "월별 상세 지출 내역", description = "특정 연월의 구독별 상세 지출 내역을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<MonthlyExpenseDetailResponse>> getMonthlyExpenseDetail(
            @CurrentUserId Long userId,
            @Parameter(description = "연도 (예: 2025)") int year,
            @Parameter(description = "월 (1~12)") int month);
}
