package com.scrumble.gudocs.expense.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.expense.dto.response.CategoryExpenseResponse;
import com.scrumble.gudocs.expense.dto.response.ExpenseTrendResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseDetailResponse;
import com.scrumble.gudocs.expense.dto.response.MonthlyExpenseResponse;
import com.scrumble.gudocs.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Expenses", description = "지출 분석 API")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/subscriptions/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @Operation(summary = "월별 지출 분석", description = "특정 연월의 구독 총 지출 및 전월 대비 변화율을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<MonthlyExpenseResponse>> getMonthlyExpense(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "연도 (예: 2025)") @RequestParam int year,
            @Parameter(description = "월 (1~12)") @RequestParam int month) {
        MonthlyExpenseResponse response = expenseService.getMonthlyExpense(userDetails.getUsername(), year, month);
        return ResponseEntity.ok(ApiResponse.success("월별 구독 지출 분석 조회에 성공했습니다.", response));
    }

    @Operation(summary = "카테고리별 지출 분석", description = "특정 연월의 카테고리별 구독 지출을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryExpenseResponse>> getCategoryExpense(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "연도 (예: 2025)") @RequestParam int year,
            @Parameter(description = "월 (1~12)") @RequestParam int month) {
        CategoryExpenseResponse response = expenseService.getCategoryExpense(userDetails.getUsername(), year, month);
        return ResponseEntity.ok(ApiResponse.success("카테고리별 지출 분석 조회에 성공했습니다.", response));
    }

    @Operation(summary = "최근 6개월 지출 추이", description = "기준 월을 포함한 최근 6개월간의 월별 지출 추이를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<ExpenseTrendResponse>> getExpenseTrend(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "기준 연도 (예: 2025)") @RequestParam int baseYear,
            @Parameter(description = "기준 월 (1~12)") @RequestParam int baseMonth) {
        ExpenseTrendResponse response = expenseService.getExpenseTrend(userDetails.getUsername(), baseYear, baseMonth);
        return ResponseEntity.ok(ApiResponse.success("최근 6개월 구독 지출 추이 조회에 성공했습니다.", response));
    }

    @Operation(summary = "월별 상세 지출 내역", description = "특정 연월의 구독별 상세 지출 내역을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    @GetMapping("/monthly/details")
    public ResponseEntity<ApiResponse<MonthlyExpenseDetailResponse>> getMonthlyExpenseDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "연도 (예: 2025)") @RequestParam int year,
            @Parameter(description = "월 (1~12)") @RequestParam int month) {
        MonthlyExpenseDetailResponse response = expenseService.getMonthlyExpenseDetail(userDetails.getUsername(), year, month);
        return ResponseEntity.ok(ApiResponse.success("월별 구독 서비스 상세 지출 내역 조회에 성공했습니다.", response));
    }
}
