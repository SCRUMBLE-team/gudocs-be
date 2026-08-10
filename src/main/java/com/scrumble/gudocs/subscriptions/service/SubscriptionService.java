package com.scrumble.gudocs.subscriptions.service;

import com.scrumble.gudocs.global.exception.BusinessException;
import com.scrumble.gudocs.global.exception.ErrorCode;
import com.scrumble.gudocs.subscriptions.dto.request.SavingsSelectionRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionCreateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionStatusUpdateRequest;
import com.scrumble.gudocs.subscriptions.dto.request.SubscriptionUpdateRequest;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.dto.response.SubscriptionResponse;
import com.scrumble.gudocs.subscriptions.entity.*;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.subscriptions.util.NextBillingDateCalculator;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public SubscriptionResponse create(Long userId, SubscriptionCreateRequest request) {
        User user = findUser(userId);
        ResolvedService resolved = resolveService(request.serviceName(), request.serviceCode());

        Subscription subscription = Subscription.builder()
                .user(user)
                .serviceName(resolved.name())
                .serviceCode(resolved.code())
                .category(request.category())
                .price(request.price())
                .billingCycle(request.billingCycle())
                .firstBillingDate(request.firstBillingDate())
                .build();

        return toResponse(subscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAll(Long userId) {
        User user = findUser(userId);
        LocalDate today = LocalDate.now();
        return subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(s -> SubscriptionResponse.from(s, NextBillingDateCalculator.calculate(s, today)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getOne(Long userId, Long subscriptionId) {
        User user = findUser(userId);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse update(Long userId, Long subscriptionId, SubscriptionUpdateRequest request) {
        User user = findUser(userId);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);

        ResolvedService resolved = resolveService(request.serviceName(), request.serviceCode());
        subscription.update(
                resolved.name(), resolved.code(),
                request.category(), request.price(),
                request.billingCycle(), request.firstBillingDate()
        );

        return toResponse(subscription);
    }

    @Transactional
    public void delete(Long userId, Long subscriptionId) {
        User user = findUser(userId);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        subscription.softDelete();
    }

    /**
     * 이미 등록한 서비스인지 확인한다(경고 용도).
     *
     * <p>카탈로그에서 고른 서비스면 code 로 본다 — 표시 이름은 바뀔 수 있고 사용자가 임의로 적을 수도 있어서,
     * 이름만 비교하면 같은 넷플릭스를 "Netflix"/"넷플릭스"로 두 번 등록해도 통과한다.
     * 직접 입력한 서비스는 code 가 없으므로 기존대로 이름을 대소문자 무시하고 비교한다.
     */
    @Transactional(readOnly = true)
    public boolean isDuplicateService(Long userId, String serviceName, String serviceCode) {
        User user = findUser(userId);
        if (serviceCode != null && !serviceCode.isBlank()) {
            return subscriptionRepository.existsByUserAndServiceCodeAndDeletedAtIsNull(user, serviceCode.strip());
        }
        return subscriptionRepository.existsByUserAndServiceNameIgnoreCaseAndDeletedAtIsNull(user, serviceName.strip());
    }

    @Transactional
    public SubscriptionResponse updateStatus(Long userId, Long subscriptionId,
                                             SubscriptionStatusUpdateRequest request) {
        User user = findUser(userId);
        Subscription subscription = findSubscription(subscriptionId);
        checkOwnership(subscription, user);
        subscription.updateStatus(request.status());
        return toResponse(subscription);
    }

    /**
     * 절약하기 화면에서 체크한 구독을 저장한다. 요청 목록으로 현재 선택을 <b>통째로 대체</b>하므로
     * 빈 배열이면 전체 해제이고, 같은 요청을 두 번 보내도 결과가 같다(멱등).
     *
     * <p>선택을 화면 로컬이 아니라 서버에 남기는 이유는 나중에 이 목록으로 알림을 보내야 하기 때문이다.
     * 그래서 알림 payload 에 구독 id 를 실어 보내지 않고, FE 가 화면 진입 시 이 API 로 최신 목록을 받는다
     * — payload 는 발송 시점 스냅샷이라 그 사이 해지·삭제된 구독과 어긋난다.
     *
     * <p>남의 구독이나 없는 구독 id 가 섞여 있으면 조용히 무시하지 않고 예외를 던진다. 무시하면
     * 프론트는 저장에 성공했다고 믿는데 실제로는 일부만 저장돼, 알림도 그만큼 빠진다.
     */
    @Transactional
    public List<SubscriptionResponse> replaceSavingsSelection(Long userId, SavingsSelectionRequest request) {
        User user = findUser(userId);
        Set<Long> selectedIds = Set.copyOf(request.subscriptionIds());

        List<Subscription> mine = subscriptionRepository.findAllByUserOrderByCreatedAtDesc(user);
        Set<Long> myIds = mine.stream().map(Subscription::getId).collect(Collectors.toSet());
        if (!myIds.containsAll(selectedIds)) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }

        mine.forEach(s -> s.updateSavingsSelection(selectedIds.contains(s.getId())));

        return getSavingsSelection(user);
    }

    /** 현재 선택된 절약 후보 목록. 알림을 받고 화면에 들어왔을 때 최신 상태를 그린다. */
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getSavingsSelection(Long userId) {
        return getSavingsSelection(findUser(userId));
    }

    private List<SubscriptionResponse> getSavingsSelection(User user) {
        LocalDate today = LocalDate.now();
        return subscriptionRepository.findSavingsSelectedByUser(user).stream()
                .map(s -> SubscriptionResponse.from(s, NextBillingDateCalculator.calculate(s, today)))
                .toList();
    }

    /** 저장할 서비스 이름과 코드. 등록·수정이 같은 규칙을 쓰도록 한 곳에서 만든다. */
    private record ResolvedService(String name, String code) {
    }

    /**
     * 요청의 서비스명·코드를 저장할 값으로 정규화한다.
     *
     * <p>code 가 있으면 <b>이름을 클라이언트 말대로 믿지 않고</b> 카탈로그의 canonicalName 으로 덮어쓴다.
     * 안 그러면 {@code serviceCode=NETFLIX} + {@code serviceName="동네 헬스장"} 같은 모순된 행이
     * 저장돼, 목록에는 헬스장으로 뜨는데 로고는 넷플릭스가 붙는다.
     *
     * <p>category 는 덮어쓰지 않는다. 이름·코드가 서비스의 <i>정체성</i>인 것과 달리 카테고리는 사용자가
     * 자기 기준으로 분류하는 값이고(지출 분석의 묶음 단위), 기존 API 도 임의 카테고리를 허용해 왔다.
     *
     * <p>카탈로그에 없는 code, 신규 등록 대상이 아닌 code 는 여기서 막는다 — 잘못 저장되면
     * 프론트가 영영 로고를 못 찾거나, 실제로 결제하지 않는 구독이 지출에 잡힌다.
     */
    private ResolvedService resolveService(String serviceName, String serviceCode) {
        if (serviceCode == null || serviceCode.isBlank()) {
            return new ResolvedService(serviceName.strip(), null);
        }

        ServiceCatalog.CatalogService service = ServiceCatalog.findByCode(serviceCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN_SERVICE_CODE));
        if (!service.selectable()) {
            throw new BusinessException(ErrorCode.SERVICE_NOT_SELECTABLE);
        }
        return new ResolvedService(service.canonicalName(), service.code());
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return SubscriptionResponse.from(
                subscription,
                NextBillingDateCalculator.calculate(subscription, LocalDate.now())
        );
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Subscription findSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (subscription.isDeleted()) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        return subscription;
    }

    private void checkOwnership(Subscription subscription, User user) {
        if (!Objects.equals(subscription.getUser().getId(), user.getId())) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_FORBIDDEN);
        }
    }
}
