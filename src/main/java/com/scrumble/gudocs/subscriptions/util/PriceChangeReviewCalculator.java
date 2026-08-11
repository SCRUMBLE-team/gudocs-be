package com.scrumble.gudocs.subscriptions.util;

import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog.PriceChange;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionStatus;

import java.time.LocalDate;

/**
 * 공식 가격 변경 뒤 사용자가 자기 결제 금액을 확인할 시점인지 계산한다.
 *
 * <p>이 계산은 카드·은행의 실제 결제를 확인하지 않는다. 구독 앵커와 주기로 계산한
 * "적용일 이후 첫 예정 결제일"이 지났는지만 판정하며, 결과는 서비스 재접속 시 배너를
 * 보여주기 위한 힌트로만 사용한다. 구독 금액을 자동으로 수정하거나 푸시를 발송하지 않는다.
 */
public final class PriceChangeReviewCalculator {

    private PriceChangeReviewCalculator() {
    }

    public static boolean isRequired(Subscription subscription, PriceChange change, LocalDate today) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                || !today.isAfter(change.effectiveOn())) {
            return false;
        }

        LocalDate firstScheduledBillingAfterChange =
                NextBillingDateCalculator.calculate(subscription, change.effectiveOn());
        return firstScheduledBillingAfterChange.isBefore(today);
    }
}
