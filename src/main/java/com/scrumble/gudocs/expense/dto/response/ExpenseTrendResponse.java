package com.scrumble.gudocs.expense.dto.response;

import java.util.List;

public record ExpenseTrendResponse(
        int baseYear,
        int baseMonth,
        List<MonthlyTrendItem> monthlyTrends
) {
}
