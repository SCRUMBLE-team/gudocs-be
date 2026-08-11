package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog.DeclaredPriceChange;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
 * 한 변경 건에 대해 구독마다 <b>두 단계</b>를 각 1회씩 보낸다(단계는 dedup 키의 remind_offset).
 * <ol>
 *   <li><b>발표 시점</b>(적용일 전) — "9월 1일부터 19,000원으로 인상될 예정이에요". 서비스의
 *       <b>공식 안내 페이지</b>로 보낸다. 가격 변경은 원문 확인이 가장 확실하기 때문이다.</li>
 *   <li><b>적용일 이후 그 구독의 결제일이 지난 뒤</b> — "이번 결제 금액, 확인하셨나요?".
 *       <b>구독 수정 화면</b>으로 보낸다. 이때가 새 금액이 실제로 빠져나간 직후라 사용자가
 *       자기 결제 내역을 보고 판단할 수 있다.</li>
 * </ol>
 * 적용일과 실제 청구 시점은 사용자마다 최대 한 달(연간 결제면 최대 1년) 벌어지므로, ②를 적용일에
 * 일괄로 보내면 아직 옛 금액을 내고 있는 사람에게 묻는 셈이 된다.
 * <p>
 * <b>사용자의 결제 금액을 자동으로 바꾸지 않는다.</b> 기존가 유지·프로모션·제휴결합·인앱결제로 사람마다
 * 실제 청구액이 다르고, 적용일이 지나도 그 사람들은 계속 옛 금액을 낸다. 서버가 대신 고치면 본인이
 * 아무것도 하지 않았는데 지출 분석이 조용히 틀리고, 틀렸다는 것을 알릴 방법도 없다. 실제 금액을 아는
 * 사람은 사용자뿐이므로 수정 화면으로 보내 직접 확인하게 한다(기존 {@code PUT /api/subscriptions/{id}}).
 * 수정하고 나면 구가격과 더 이상 일치하지 않아 대상에서 저절로 빠진다.
 */
@Service
@RequiredArgsConstructor
public class PriceChangeDispatchService {

    /** 발송 단계(dedup 키의 remind_offset). 한 변경 건에 대해 구독마다 각 단계를 1회씩만 보낸다. */
    private static final int ANNOUNCE_STAGE = 0;
    private static final int CONFIRM_STAGE = 1;

    /** 본문에 쓰는 적용일 표기. 연도는 붙이지 않는다(가까운 미래라 "9월 1일"로 충분하다). */
    private static final DateTimeFormatter EFFECTIVE_ON_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA);

    /** 금액 확인 알림이 여는 구독 수정 화면. */
    private static final String SUBSCRIPTION_EDIT_PATH = "/subscriptions/%d/edit";

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationSender notificationSender;

    @Value("${app.firebase.frontend-base-url}")
    private String frontendBaseUrl;

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
            for (Subscription subscription : targetsOf(declared)) {
                draftFor(declared, subscription, today)
                        .ifPresent(draft -> notificationSender.send(subscription.getUser().getId(), draft));
            }
        }
    }

    /**
     * 아직 구가격을 쓰고 있는 활성 구독. 이미 새 금액으로 수정한 사용자는 여기서 빠지므로
     * "확인했음"을 따로 저장하지 않아도 안내가 반복되지 않는다.
     */
    private List<Subscription> targetsOf(DeclaredPriceChange declared) {
        return subscriptionRepository.findActiveByServiceCodeAndPriceAndBillingCycle(
                declared.service().code(), declared.change().oldPrice(), declared.plan().billingCycle());
    }

    /**
     * 이 구독이 오늘 받을 알림. 두 단계 중 하나이며, 아직 어느 단계도 아니면 비어 있다.
     * 이미 보낸 단계는 {@link NotificationSender}의 dedup 이 걸러내므로 매일 다시 계산해도 안전하다.
     */
    private Optional<NotificationDraft> draftFor(DeclaredPriceChange declared, Subscription subscription,
                                                 LocalDate today) {
        if (!today.isAfter(declared.change().effectiveOn())) {
            return Optional.of(announceDraft(declared, subscription));  // 적용 당일까지는 발표 단계
        }
        return isChargedAtNewPrice(declared, subscription, today)
                ? Optional.of(confirmDraft(declared, subscription))
                : Optional.empty();  // 적용은 됐지만 이 사용자의 결제일은 아직 안 지났다
    }

    /**
     * 적용일 이후 이 구독의 첫 결제일이 이미 지났는지. 그때부터는 <b>새 금액이 실제로 빠져나간 뒤</b>라
     * 사용자가 자기 결제 내역을 보고 확인할 수 있다.
     * <p>
     * 사용자마다 결제일이 달라 적용일과 실제 청구 시점은 최대 한 달(연간 결제면 최대 1년) 벌어진다.
     * 적용일에 일괄로 물으면 아직 옛 금액을 내고 있는 사람에게 묻는 셈이 된다.
     */
    private boolean isChargedAtNewPrice(DeclaredPriceChange declared, Subscription subscription, LocalDate today) {
        LocalDate firstBillingAfterChange =
                NextBillingDateCalculator.calculate(subscription, declared.change().effectiveOn());
        return firstBillingAfterChange.isBefore(today);
    }

    /** ① 발표 시점 — 공식 안내로 보내 원문을 확인하게 한다. */
    private NotificationDraft announceDraft(DeclaredPriceChange declared, Subscription subscription) {
        ServiceCatalog.PriceChange change = declared.change();
        long oldPrice = change.oldPrice();
        long newPrice = change.newPrice();
        return new NotificationDraft(
                NotificationType.PRICE_CHANGE,
                // targetDate = 적용 예정일. dedup 키에 들어가므로 "이 변경 건"의 식별자 역할을 겸한다.
                change.effectiveOn(),
                ANNOUNCE_STAGE,
                subscription.getId(),
                subscription.getServiceName() + " 요금이 변경될 예정이에요",
                String.format(Locale.KOREA, "%s 요금제가 %s부터 %,d원으로 %s될 예정이에요. 공식 안내를 확인해보세요.",
                        declared.plan().name(), change.effectiveOn().format(EFFECTIVE_ON_FORMAT),
                        newPrice, newPrice > oldPrice ? "인상" : "인하"),
                Map.of("type", NotificationType.PRICE_CHANGE.name(),
                        "subscriptionId", String.valueOf(subscription.getId()),
                        // 다른 알림과 달리 앱 안이 아니라 서비스의 공식 안내로 보낸다 — 가격 변경은
                        // 원문을 직접 확인하는 것이 사용자에게 가장 확실한 정보이기 때문이다.
                        "link", change.sourceUrl()));
    }

    /** ② 결제가 지난 뒤 — 실제 결제 금액을 보고 수정할 수 있게 구독 수정 화면으로 보낸다. */
    private NotificationDraft confirmDraft(DeclaredPriceChange declared, Subscription subscription) {
        ServiceCatalog.PriceChange change = declared.change();
        return new NotificationDraft(
                NotificationType.PRICE_CHANGE,
                change.effectiveOn(),
                CONFIRM_STAGE,
                subscription.getId(),
                "이번 " + subscription.getServiceName() + " 결제 금액, 확인하셨나요?",
                String.format(Locale.KOREA,
                        "최근 공식 요금이 %,d원으로 변경됐어요. 실제 결제 금액이 달라졌다면 업데이트해주세요.",
                        change.newPrice()),
                Map.of("type", NotificationType.PRICE_CHANGE.name(),
                        "subscriptionId", String.valueOf(subscription.getId()),
                        "link", frontendBaseUrl + SUBSCRIPTION_EDIT_PATH.formatted(subscription.getId())));
    }
}
