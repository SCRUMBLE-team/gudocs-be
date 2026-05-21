package com.scrumble.gudocs.subscriptions.expense.dto.response;

public record MonthlyExpenseResponse(
        int year,
        int month,
        long totalAmount,
        long previousMonthAmount,
        long changeAmount,
        double changeRate,
        long monthlySubscriptionAmount,
        long annualSubscriptionMonthlyConvertedAmount
) {
}
