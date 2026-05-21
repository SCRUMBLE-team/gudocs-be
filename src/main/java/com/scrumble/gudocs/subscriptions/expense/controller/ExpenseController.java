package com.scrumble.gudocs.subscriptions.expense.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.subscriptions.expense.dto.response.CategoryExpenseResponse;
import com.scrumble.gudocs.subscriptions.expense.dto.response.ExpenseTrendResponse;
import com.scrumble.gudocs.subscriptions.expense.dto.response.MonthlyExpenseDetailResponse;
import com.scrumble.gudocs.subscriptions.expense.dto.response.MonthlyExpenseResponse;
import com.scrumble.gudocs.subscriptions.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<MonthlyExpenseResponse>> getMonthlyExpense(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {
        MonthlyExpenseResponse response = expenseService.getMonthlyExpense(userDetails.getUsername(), year, month);
        return ResponseEntity.ok(ApiResponse.success("월별 구독 지출 분석 조회에 성공했습니다.", response));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryExpenseResponse>> getCategoryExpense(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {
        CategoryExpenseResponse response = expenseService.getCategoryExpense(userDetails.getUsername(), year, month);
        return ResponseEntity.ok(ApiResponse.success("카테고리별 지출 분석 조회에 성공했습니다.", response));
    }

    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<ExpenseTrendResponse>> getExpenseTrend(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int baseYear,
            @RequestParam int baseMonth) {
        ExpenseTrendResponse response = expenseService.getExpenseTrend(userDetails.getUsername(), baseYear, baseMonth);
        return ResponseEntity.ok(ApiResponse.success("최근 6개월 구독 지출 추이 조회에 성공했습니다.", response));
    }

    @GetMapping("/monthly/details")
    public ResponseEntity<ApiResponse<MonthlyExpenseDetailResponse>> getMonthlyExpenseDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {
        MonthlyExpenseDetailResponse response = expenseService.getMonthlyExpenseDetail(userDetails.getUsername(), year, month);
        return ResponseEntity.ok(ApiResponse.success("월별 구독 서비스 상세 지출 내역 조회에 성공했습니다.", response));
    }
}
