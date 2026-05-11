package com.scrumble.gudocs.dashboard.dto;

import java.time.LocalDate;

public record UpcomingNotification(
        Long subscriptionId,
        String serviceName,
        Long price,
        LocalDate nextBillingDate,
        int daysUntilBilling
) {
}
