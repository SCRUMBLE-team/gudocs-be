package com.scrumble.gudocs.billing.scheduler;

import com.scrumble.gudocs.billing.service.BillingRecordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 그날 결제 예정일이 도래한 구독의 청구 스냅샷을 남기는 배치.
 *
 * <p>알림 스케줄러와 달리 <b>기능 플래그로 끄지 않는다</b> — 알림은 안 보내도 서비스가 굴러가지만
 * 이 배치가 멈추면 그 기간의 지출 기록이 영영 비고, 나중에 정확히 복원할 방법이 없다.
 */
@Component
@RequiredArgsConstructor
public class BillingRecordScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingRecordScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final BillingRecordService billingRecordService;

    @Scheduled(cron = "${app.billing.record-cron}", zone = "Asia/Seoul")
    public void recordDueBillings() {
        LocalDate today = LocalDate.now(ZONE);
        log.info("결제 기록 스케줄러 시작 today={}", today);
        try {
            billingRecordService.recordDueBillings(today);
        } catch (RuntimeException e) {
            // 배치 예외를 삼켜 로그로 남긴다. 단일 스레드 스케줄러라 예외가 다른 배치 실행에 영향을 준다.
            log.error("결제 기록 스케줄러 실패 today={}", today, e);
        }
    }
}
