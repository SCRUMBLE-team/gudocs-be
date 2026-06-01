package com.scrumble.gudocs.notification.dto.response;

import java.time.LocalDate;

public record UpcomingNotification(
        Long subscriptionId,
        String serviceName,
        Long price,
        LocalDate nextBillingDate,
        int daysUntilBilling
) {
}
