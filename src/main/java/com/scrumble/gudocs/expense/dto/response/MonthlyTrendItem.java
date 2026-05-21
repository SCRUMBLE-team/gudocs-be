package com.scrumble.gudocs.expense.dto.response;

public record MonthlyTrendItem(
        int year,
        int month,
        long totalAmount
) {
}
