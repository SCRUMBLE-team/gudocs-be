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
 * 가격 변경 발표는 <b>적용일까지 구독별 1회만</b> 보내고, 클릭하면 서비스의 공식 안내 페이지로
 * 이동한다. 적용 후 결제 금액 확인은 푸시로 재촉하지 않는다. 사용자가 서비스에 다시 접속했을 때
 * {@code SubscriptionResponse.priceReviewRequired}를 보고 프론트가 배너를 노출한다.
 * <p>
 * <b>사용자의 결제 금액을 자동으로 바꾸지 않는다.</b> 기존가 유지·프로모션·제휴결합·인앱결제로 사람마다
 * 실제 청구액이 다르고, 적용일이 지나도 그 사람들은 계속 옛 금액을 낸다. 서버가 대신 고치면 본인이
 * 아무것도 하지 않았는데 지출 분석이 조용히 틀리고, 틀렸다는 것을 알릴 방법도 없다. 실제 금액을 아는
 * 사람은 사용자뿐이므로 서비스 재접속 시 배너에서 확인을 요청하고, 사용자가 원할 때 기존
 * {@code PUT /api/subscriptions/{id}}로 직접 수정한다. 수정하고 나면 구가격과 더 이상 일치하지 않아
 * 배너 대상에서도 저절로 빠진다.
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
            if (declared.change().effectiveOn().isBefore(today)) {
                continue;
            }
            for (Subscription subscription : targetsOf(declared)) {
                notificationSender.send(subscription.getUser().getId(), announceDraft(declared, subscription));
            }
        }
    }

    /**
     * 아직 구가격을 쓰고 있는 활성 구독. 공식 변경 발표를 받을 보수적인 대상 조건이다.
     */
    private List<Subscription> targetsOf(DeclaredPriceChange declared) {
        return subscriptionRepository.findActiveByServiceCodeAndPriceAndBillingCycle(
                declared.service().code(), declared.change().oldPrice(), declared.plan().billingCycle());
    }

    /** 공식 변경 발표 — 공식 안내로 보내 원문을 확인하게 한다. */
    private NotificationDraft announceDraft(DeclaredPriceChange declared, Subscription subscription) {
        return announceDraft(subscription, declared.plan().name(), declared.change());
    }

    /**
     * 가격 변경 알림 1건의 문구·페이로드. 카탈로그 레코드가 아니라 <b>요금제명 + 변경 내용</b>만 받는다.
     *
     * <p>시연·검증용 미리보기({@code NotificationPreviewService})가 이 문구 생성기를 그대로 쓰기
     * 위해서다. 미리보기가 문구를 따로 만들면 시연에서 보여주는 게 진짜 알림이 아니게 되고, 문구를
     * 고칠 때 두 곳이 갈라진다. 카탈로그에는 아직 선언된 변경이 없어 미리보기가 {@link DeclaredPriceChange}
     * 를 넘길 수 없으므로, 공유 지점을 레코드가 아니라 이 값들로 잡는다.
     */
    NotificationDraft announceDraft(Subscription subscription, String planName,
                                    ServiceCatalog.PriceChange change) {
        long oldPrice = change.oldPrice();
        long newPrice = change.newPrice();
        return new NotificationDraft(
                NotificationType.PRICE_CHANGE,
                // targetDate = 적용 예정일. dedup 키에 들어가므로 "이 변경 건"의 식별자 역할을 겸한다.
                change.effectiveOn(),
                0,
                subscription.getId(),
                subscription.getServiceName() + " 요금이 변경될 예정이에요",
                String.format(Locale.KOREA, "%s 요금제가 %s부터 %,d원으로 %s될 예정이에요. 공식 안내를 확인해보세요.",
                        planName, change.effectiveOn().format(EFFECTIVE_ON_FORMAT),
                        newPrice, newPrice > oldPrice ? "인상" : "인하"),
                Map.of("type", NotificationType.PRICE_CHANGE.name(),
                        "subscriptionId", String.valueOf(subscription.getId()),
                        // 다른 알림과 달리 앱 안이 아니라 서비스의 공식 안내로 보낸다 — 가격 변경은
                        // 원문을 직접 확인하는 것이 사용자에게 가장 확실한 정보이기 때문이다.
                        "link", change.sourceUrl()));
    }
}
