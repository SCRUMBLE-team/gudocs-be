package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.notification.dto.request.NotificationPreviewRequest;
import com.scrumble.gudocs.notification.dto.response.NotificationPreviewResponse;
import com.scrumble.gudocs.notification.dto.response.PushTestResponse;
import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.notification.push.PushMessage;
import com.scrumble.gudocs.notification.push.PushSender;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 결제 예정·해지 알림을 원하는 순간에 띄우는 미리보기 발송(시연·검증용).
 *
 * <p><b>왜 필요한가</b>: 실제 배치는 ① cron 시각에만 돌고 ② D-3·당일에 해당하는 구독만 고르며
 * ③ {@code user_notifications} 의 UNIQUE 제약 때문에 <b>같은 단계는 한 번만</b> 나간다. 그래서
 * 리허설을 한 번 하면 정작 본 발표에서는 아무것도 오지 않는다.
 *
 * <p><b>일반 테스트 푸시({@link PushTestService})와 다른 점</b>: 그쪽은 고정 문구에 link 가 없어
 * 눌러도 이동하지 않는다. 전달 경로 진단용이다. 이쪽은 배치와 <b>같은 draft 생성 코드</b>
 * ({@code NotificationDispatchService.toBillingDraft/toCancelDraft})를 재사용하므로 문구도 이동 링크도
 * 실제 알림과 동일하다.
 *
 * <p><b>{@link NotificationSender} 를 거치지 않는다.</b> 거치면 dedup 에 막혀 두 번째 호출부터
 * 발송되지 않고, {@code user_notifications} 에 행이 남아 <b>그날 진짜 알림을 잡아먹는다.</b>
 * 그래서 이력을 남기지 않고 {@link PushSender} 로 직접 보낸다 — 몇 번이든 반복할 수 있다.
 *
 * <p>대신 이 API 로는 dedup·발송 단계 판정이 검증되지 않는다. 그건 배치 테스트의 몫이다.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreviewService {

    /** 미리보기를 허용하는 알림 종류. 검사 유도는 유저 단위라 구독을 지정하는 이 입력으로 표현되지 않는다. */
    private static final Set<NotificationType> SUPPORTED = Set.of(
            NotificationType.BILLING_REMINDER, NotificationType.CANCEL_REMINDER, NotificationType.PRICE_CHANGE);

    /** 실제 발송 단계와 같은 값만 허용한다 — 문구가 "3일 후"/"오늘"로 갈린다. */
    private static final Set<Integer> ALLOWED_DAYS_UNTIL = Set.of(3, 0);

    private final SubscriptionRepository subscriptionRepository;
    private final PushRegistrationRepository pushRegistrationRepository;
    private final NotificationDispatchService dispatchService;
    private final PriceChangeDispatchService priceChangeDispatchService;
    private final PushSender pushSender;

    @Transactional(readOnly = true)
    public NotificationPreviewResponse send(Long userId, NotificationPreviewRequest request) {
        if (!SUPPORTED.contains(request.type())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PREVIEW_TYPE);
        }

        Subscription subscription = subscriptionRepository.findById(request.subscriptionId())
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        // 남의 구독으로 남의 기기에 푸시를 쏘지 못하게 한다. 진단용이라도 소유권은 그대로 검사한다.
        if (!subscription.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_FORBIDDEN);
        }

        NotificationDraft draft = request.type() == NotificationType.PRICE_CHANGE
                ? priceChangeDraft(subscription, request)
                : billingDraft(subscription, request);

        PushMessage message = new PushMessage(draft.title(), draft.body(), draft.pushData());
        List<PushRegistration> registrations = pushRegistrationRepository.findByUserIdAndEnabledTrue(userId);
        List<PushTestResponse.DeviceResult> results = registrations.stream()
                .map(r -> new PushTestResponse.DeviceResult(
                        r.getId(), mask(r.getFid()), pushSender.send(r.getFid(), message).name()))
                .toList();

        return new NotificationPreviewResponse(
                draft.type().name(), draft.title(), draft.body(), draft.pushData().get("link"),
                pushSender.getClass().getSimpleName(), results.size(), results);
    }

    /** 결제 예정·해지 알림. 문구에 필요한 값이 전부 구독에 있어 <b>실제로 받게 될 알림과 동일</b>하다. */
    private NotificationDraft billingDraft(Subscription subscription, NotificationPreviewRequest request) {
        if (!ALLOWED_DAYS_UNTIL.contains(request.daysUntil())) {
            throw new BusinessException(ErrorCode.INVALID_PREVIEW_DAYS_UNTIL);
        }
        DueBilling due = new DueBilling(
                subscription, LocalDate.now().plusDays(request.daysUntil()), request.daysUntil());
        return request.type() == NotificationType.CANCEL_REMINDER
                ? dispatchService.toCancelDraft(due)
                : dispatchService.toBillingDraft(due);
    }

    /**
     * 가격 변경 알림. 다른 두 종류와 달리 문구의 출처가 구독이 아니라 <b>카탈로그에 선언된 변경</b>인데,
     * 선언은 실제 공식 발표를 사람이 확인했을 때만 들어간다. 그래서 미리보기는 요청으로 받은 값으로
     * {@link ServiceCatalog.PriceChange} 를 만들어 넘긴다.
     *
     * <p><b>이것은 형식 미리보기다.</b> 결제·해지 미리보기가 "실제로 받게 될 그 알림"인 것과 달리,
     * 여기 실리는 변경 내용은 검증된 공식 발표가 아니다. 문구·페이로드 생성은 배치와 같은 코드
     * ({@code PriceChangeDispatchService.announceDraft})를 쓰므로 형식만은 실제와 같다.
     */
    private NotificationDraft priceChangeDraft(Subscription subscription, NotificationPreviewRequest request) {
        if (request.newPrice() == null || request.effectiveOn() == null) {
            throw new BusinessException(ErrorCode.PRICE_CHANGE_PREVIEW_REQUIRES_CHANGE);
        }
        long oldPrice = subscription.getPrice();
        if (request.newPrice() == oldPrice) {
            // 같으면 "인상"도 "인하"도 아닌 문구가 나가 시연에서 무슨 알림인지 알 수 없다.
            throw new BusinessException(ErrorCode.PRICE_CHANGE_PREVIEW_SAME_PRICE);
        }

        ServiceCatalog.PriceChange change = new ServiceCatalog.PriceChange(
                oldPrice, request.newPrice(), request.effectiveOn(),
                LocalDate.now(),                       // 발표일 = 오늘
                resolveSourceUrl(subscription, request));

        return priceChangeDispatchService.announceDraft(subscription, resolvePlanName(subscription), change);
    }

    /**
     * 문구에 쓸 요금제명. 실제 배치는 카탈로그 요금제명을 쓰므로 같은 방식으로 찾고
     * (서비스 코드 + 현재 금액), 직접 입력한 구독처럼 못 찾으면 서비스명으로 대신한다.
     */
    private String resolvePlanName(Subscription subscription) {
        return ServiceCatalog.findByCode(subscription.getServiceCode())
                .flatMap(service -> service.planByPrice(subscription.getPrice()))
                .map(ServiceCatalog.Plan::name)
                .orElseGet(subscription::getServiceName);
    }

    /**
     * 알림을 눌렀을 때 열 링크. 요청에 있으면 그것을 쓰고, 없으면 카탈로그의 서비스 링크로 대신한다.
     * 둘 다 없으면 링크 없는 알림을 보내는 대신 400으로 알린다 — 시연에서 눌러도 아무 일이 없으면
     * 기능이 고장 난 것처럼 보인다.
     */
    private String resolveSourceUrl(Subscription subscription, NotificationPreviewRequest request) {
        if (request.sourceUrl() != null && !request.sourceUrl().isBlank()) {
            return request.sourceUrl().strip();
        }
        String cancelUrl = ServiceCatalog.cancelUrlOf(subscription.getServiceCode());
        if (cancelUrl == null) {
            throw new BusinessException(ErrorCode.PRICE_CHANGE_PREVIEW_NO_SOURCE_URL);
        }
        return cancelUrl;
    }

    /** fid 전체 값은 로그·응답에 남기지 않는다. */
    private String mask(String fid) {
        if (fid == null || fid.length() <= 6) {
            return "***";
        }
        return fid.substring(0, 6) + "***";
    }
}
