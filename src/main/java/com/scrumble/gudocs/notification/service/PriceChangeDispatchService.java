package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog.DeclaredPriceChange;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 공식 가격 변경 알림 발송 배치.
 * <p>
 * 대상은 {@link ServiceCatalog}에 선언된 가격 변경 예고다. <b>크롤링하지 않는다</b> — 공식 출처 확인은
 * 사람이 하고, 확인 결과를 카탈로그에 적어 배포하는 것이 곧 검증이다(카탈로그 주석 참고). 그래서 이
 * 배치가 하는 일은 "선언된 변경을 그 요금제 사용자에게 전달"뿐이고, 수집 실패 같은 실패 모드가 없다.
 * <p>
 * <b>구독별 1건</b>으로 발송한다 — 어느 구독 이야기인지 특정해야 문구에 요금제명을 넣을 수 있고
 * dedup 도 구독 단위로 걸린다. 같은 사용자가 대상 구독을 둘 가지고 있으면 알림도 둘이지만,
 * 한 시점에 선언된 변경은 보통 한두 건이라 실제로 겹치는 경우가 거의 없다.
 * <p>
 * <b>클릭하면 앱이 아니라 서비스의 공식 안내 페이지로 나간다</b>(다른 알림은 앱 내부로 보낸다).
 * 가격 변경은 원문을 직접 확인하는 것이 가장 확실하기 때문이다. 앱 안에서 이어서 처리하려면
 * payload 의 {@code subscriptionId} 로 구독 상세 경로를 만들면 된다.
 * <p>
 * <b>사용자의 결제 금액을 자동으로 바꾸지 않는다.</b> 기존가 유지·프로모션·제휴결합·인앱결제로 사람마다
 * 실제 청구액이 다르므로, 서버가 대신 고치면 지출 분석이 사실과 어긋난다. 반영은 사용자가 상세 화면에서
 * 직접 고른다(기존 {@code PUT /api/subscriptions/{id}}).
 */
@Service
@RequiredArgsConstructor
public class PriceChangeDispatchService {

    /** 본문에 쓰는 적용일 표기. 연도는 붙이지 않는다(가까운 미래라 "9월 1일"로 충분하다). */
    private static final DateTimeFormatter EFFECTIVE_ON_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA);

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationSender notificationSender;

    /**
     * 카탈로그에 선언된 가격 변경 예고 중 아직 적용되지 않은 건을 찾아, 해당 요금제를 쓰는 사용자에게
     * 구독별로 알림을 보낸다. 이미 보낸 대상은 {@link NotificationSender}의 dedup 이 걸러낸다.
     */
    public void dispatchDeclaredPriceChanges(LocalDate today) {
        dispatch(ServiceCatalog.declaredPriceChanges(), today);
    }

    /**
     * 선언 목록을 직접 받는 진입점. 카탈로그는 정적 데이터라 평소 선언이 비어 있어서,
     * 테스트가 실제 인상 발표를 카탈로그에 심지 않고도 발송 규칙을 검증할 수 있게 열어 둔다.
     */
    void dispatch(List<DeclaredPriceChange> declaredChanges, LocalDate today) {
        for (DeclaredPriceChange declared : declaredChanges) {
            if (isApplied(declared, today)) {
                continue;
            }
            for (Subscription subscription : targetsOf(declared)) {
                notificationSender.send(subscription.getUser().getId(), toDraft(declared, subscription));
            }
        }
    }

    /**
     * 적용일이 지난 예고는 건너뛴다. 적용 후에는 카탈로그의 price 를 새 가격으로 올리고 예고를 지우는 것이
     * 정상 절차지만, 지우는 것을 잊었을 때 뒤늦게 "곧 바뀌어요" 알림이 나가지 않게 하는 안전장치다.
     */
    private boolean isApplied(DeclaredPriceChange declared, LocalDate today) {
        return declared.change().effectiveOn().isBefore(today);
    }

    /** 변경 대상 요금제를 쓰고 있는 활성 구독. 카탈로그의 현재 price 가 곧 변경 전 금액이다. */
    private List<Subscription> targetsOf(DeclaredPriceChange declared) {
        return subscriptionRepository.findActiveByServiceCodeAndPriceAndBillingCycle(
                declared.service().code(), declared.plan().price(), declared.plan().billingCycle());
    }

    private NotificationDraft toDraft(DeclaredPriceChange declared, Subscription subscription) {
        ServiceCatalog.PriceChange change = declared.change();
        Map<String, String> data = Map.of(
                "type", NotificationType.PRICE_CHANGE.name(),
                "subscriptionId", String.valueOf(subscription.getId()),
                // 다른 알림과 달리 앱 안이 아니라 서비스의 공식 안내로 보낸다 — 가격 변경은
                // 원문을 직접 확인하는 것이 사용자에게 가장 확실한 정보이기 때문이다.
                // 앱 내부 화면으로 보내고 싶으면 subscriptionId 로 구독 상세 경로를 만들면 된다.
                "link", change.sourceUrl()
        );
        return new NotificationDraft(
                NotificationType.PRICE_CHANGE,
                // targetDate = 적용 예정일. dedup 키에 들어가므로 "이 변경 건"의 식별자 역할을 겸한다.
                change.effectiveOn(),
                0,
                subscription.getId(),
                subscription.getServiceName() + " 요금이 변경될 예정이에요",
                body(declared),
                data);
    }

    /**
     * 인상과 인하는 사용자에게 전혀 다른 소식이라 문구를 나눈다. 같은 금액인 예고는 카탈로그
     * 정합성 테스트가 막으므로 둘 중 하나로 갈린다.
     */
    private String body(DeclaredPriceChange declared) {
        long oldPrice = declared.plan().price();
        long newPrice = declared.change().newPrice();
        return String.format(Locale.KOREA, "%s 요금제가 %s부터 %,d원으로 %s될 예정이에요. 공식 안내를 확인해보세요.",
                declared.plan().name(),
                declared.change().effectiveOn().format(EFFECTIVE_ON_FORMAT),
                newPrice,
                newPrice > oldPrice ? "인상" : "인하");
    }
}
