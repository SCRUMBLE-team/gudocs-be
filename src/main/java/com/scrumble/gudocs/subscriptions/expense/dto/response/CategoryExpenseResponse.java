package com.scrumble.gudocs.subscriptions.expense.dto.response;

import java.util.List;

public record CategoryExpenseResponse(
        int year,
        int month,
        long totalAmount,
        List<CategoryExpenseItem> categories
) {
}
