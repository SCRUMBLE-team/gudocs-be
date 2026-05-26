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
import org.springframework.security.core.userdetails.UserDetails;

@Tag(name = "Expenses", description = "지출 분석 API")
@SecurityRequirement(name = "cookieAuth")
public interface ExpenseApi {

    @Operation(summary = "월별 지출 분석", description = "특정 연월의 구독 총 지출 및 전월 대비 변화율을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<MonthlyExpenseResponse>> getMonthlyExpense(
            @Parameter(hidden = true) UserDetails userDetails,
            @Parameter(description = "연도 (예: 2025)") int year,
            @Parameter(description = "월 (1~12)") int month);

    @Operation(summary = "카테고리별 지출 분석", description = "특정 연월의 카테고리별 구독 지출을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<CategoryExpenseResponse>> getCategoryExpense(
            @Parameter(hidden = true) UserDetails userDetails,
            @Parameter(description = "연도 (예: 2025)") int year,
            @Parameter(description = "월 (1~12)") int month);

    @Operation(summary = "최근 6개월 지출 추이", description = "기준 월을 포함한 최근 6개월간의 월별 지출 추이를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<ExpenseTrendResponse>> getExpenseTrend(
            @Parameter(hidden = true) UserDetails userDetails,
            @Parameter(description = "기준 연도 (예: 2025)") int baseYear,
            @Parameter(description = "기준 월 (1~12)") int baseMonth);

    @Operation(summary = "월별 상세 지출 내역", description = "특정 연월의 구독별 상세 지출 내역을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    ResponseEntity<ApiResponse<MonthlyExpenseDetailResponse>> getMonthlyExpenseDetail(
            @Parameter(hidden = true) UserDetails userDetails,
            @Parameter(description = "연도 (예: 2025)") int year,
            @Parameter(description = "월 (1~12)") int month);
}
