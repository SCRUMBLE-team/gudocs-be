package com.scrumble.gudocs.subscriptions.expense.dto.response;

public record MonthlyTrendItem(
        int year,
        int month,
        long totalAmount
) {
}
