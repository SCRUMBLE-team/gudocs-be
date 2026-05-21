package com.scrumble.gudocs.expense.dto.response;

import java.util.List;

public record MonthlyExpenseDetailResponse(
        int year,
        int month,
        long totalAmount,
        List<SubscriptionExpenseDetail> subscriptions
) {
}
