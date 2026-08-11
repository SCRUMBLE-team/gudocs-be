package com.scrumble.gudocs.subscriptions.entity;

public enum BillingCycle {

    MONTHLY(1),
    YEARLY(12);

    /**
     * 한 번의 결제가 커버하는 개월 수. 지출 분석에서 "월평균 부담"을 낼 때 결제액을 이 개월 수로
     * 나눠 결제일부터 그만큼의 달에 분산한다 — 연간 120,000원은 결제월부터 12개월에 10,000원씩.
     */
    private final int coverageMonths;

    BillingCycle(int coverageMonths) {
        this.coverageMonths = coverageMonths;
    }

    public int getCoverageMonths() {
        return coverageMonths;
    }
}
