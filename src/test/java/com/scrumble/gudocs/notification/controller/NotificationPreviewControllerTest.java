package com.scrumble.gudocs.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.dto.request.NotificationPreviewRequest;
import com.scrumble.gudocs.notification.entity.NotificationType;
import com.scrumble.gudocs.notification.repository.UserNotificationRepository;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.Subscription;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;
import com.scrumble.gudocs.subscriptions.repository.SubscriptionRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationPreviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserNotificationRepository userNotificationRepository;

    private MockHttpSession session;
    private User user;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        user = TestSessions.createUser(userRepository, socialAccountRepository, "테스터", "preview@example.com");
        session = TestSessions.authenticate(user);
        subscription = 구독(user, "Claude", 28400L);
    }

    private Subscription 구독(User owner, String name, long price) {
        return subscriptionRepository.save(Subscription.builder()
                .user(owner).serviceName(name).serviceCode("CLAUDE")
                .category(SubscriptionCategory.AI).price(price)
                .billingCycle(BillingCycle.MONTHLY)
                .firstBillingDate(LocalDate.now().plusDays(3))
                .build());
    }

    private String body(NotificationType type, Long subscriptionId, Integer daysUntil) throws Exception {
        return objectMapper.writeValueAsString(
                new NotificationPreviewRequest(type, subscriptionId, daysUntil, null, null, null));
    }

    private ResultActions 미리보기(NotificationType type, Long subscriptionId, Integer daysUntil) throws Exception {
        return mockMvc.perform(post("/api/push-registrations/test/preview")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(type, subscriptionId, daysUntil)));
    }

    private ResultActions 가격변경_미리보기(Long subscriptionId, Long newPrice, LocalDate effectiveOn,
                                     String sourceUrl) throws Exception {
        return mockMvc.perform(post("/api/push-registrations/test/preview")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NotificationPreviewRequest(
                        NotificationType.PRICE_CHANGE, subscriptionId, null,
                        newPrice, effectiveOn, sourceUrl))));
    }

    /** 시연에서 보여줄 문구가 실제 배치 문구와 같아야 의미가 있다. */
    @Test
    void 결제_예정_알림은_실제와_같은_문구와_링크로_발송된다() throws Exception {
        미리보기(NotificationType.BILLING_REMINDER, subscription.getId(), 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("BILLING_REMINDER"))
                .andExpect(jsonPath("$.data.title").value("Claude 결제 예정"))
                .andExpect(jsonPath("$.data.body").value("3일 후 28,400원이 결제될 예정이에요."))
                .andExpect(jsonPath("$.data.link").value(
                        org.hamcrest.Matchers.endsWith("/subscriptions/" + subscription.getId())));
    }

    @Test
    void 당일_알림은_오늘_문구로_발송된다() throws Exception {
        미리보기(NotificationType.BILLING_REMINDER, subscription.getId(), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("오늘 28,400원이 결제될 예정이에요."));
    }

    /** 해지 알림은 절약 후보 체크 여부와 무관하게 바로 띄울 수 있어야 한다(시연 준비를 줄이려고). */
    @Test
    void 해지_알림은_절약_후보_설정_없이도_발송된다() throws Exception {
        assertThat(subscription.isSavingsSelected()).isFalse();

        미리보기(NotificationType.CANCEL_REMINDER, subscription.getId(), 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CANCEL_REMINDER"))
                .andExpect(jsonPath("$.data.title").value("Claude, 계속 이용하시나요?"))
                .andExpect(jsonPath("$.data.body").value(
                        "해지 후보로 저장한 구독이에요. 3일 후 28,400원이 결제될 예정이에요."));
    }

    /**
     * 이 API 의 존재 이유. 발송 이력을 남기면 두 번째 호출이 dedup 에 막히고, 그날 진짜 알림까지
     * 중복으로 간주돼 나가지 않는다.
     */
    @Test
    void 반복_호출해도_매번_발송되고_발송_이력을_남기지_않는다() throws Exception {
        미리보기(NotificationType.BILLING_REMINDER, subscription.getId(), 3).andExpect(status().isOk());
        미리보기(NotificationType.BILLING_REMINDER, subscription.getId(), 3).andExpect(status().isOk());
        미리보기(NotificationType.BILLING_REMINDER, subscription.getId(), 3).andExpect(status().isOk());

        assertThat(userNotificationRepository.findAll()).isEmpty();
    }

    // --- 가격 변경 ---
    // 문구 출처가 구독이 아니라 카탈로그 선언이고, 그 선언은 지금 0건이라 요청 값으로 대신 만든다.

    @Test
    void 가격_인상_알림은_인상_문구로_발송된다() throws Exception {
        가격변경_미리보기(subscription.getId(), 31900L, LocalDate.of(2026, 9, 1), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("PRICE_CHANGE"))
                .andExpect(jsonPath("$.data.title").value("Claude 요금이 변경될 예정이에요"))
                .andExpect(jsonPath("$.data.body").value(
                        org.hamcrest.Matchers.containsString("9월 1일부터 31,900원으로 인상될 예정이에요")));
    }

    /** 인상/인하는 구독의 현재 가격과 비교해 자동으로 갈린다 — 인하 시연도 별도 입력 없이 된다. */
    @Test
    void 가격_인하_알림은_인하_문구로_발송된다() throws Exception {
        가격변경_미리보기(subscription.getId(), 19900L, LocalDate.of(2026, 9, 1), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value(
                        org.hamcrest.Matchers.containsString("19,900원으로 인하될 예정이에요")));
    }

    /** 가격 변경만 앱 안이 아니라 서비스의 공식 안내로 보낸다. */
    @Test
    void sourceUrl을_주면_그_링크로_이동한다() throws Exception {
        가격변경_미리보기(subscription.getId(), 31900L, LocalDate.of(2026, 9, 1),
                "https://example.com/notice")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.link").value("https://example.com/notice"));
    }

    @Test
    void sourceUrl을_생략하면_카탈로그_링크를_쓴다() throws Exception {
        가격변경_미리보기(subscription.getId(), 31900L, LocalDate.of(2026, 9, 1), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.link").value("https://claude.ai/settings/billing"));
    }

    @Test
    void 가격_변경인데_newPrice나_effectiveOn이_없으면_400() throws Exception {
        가격변경_미리보기(subscription.getId(), null, LocalDate.of(2026, 9, 1), null)
                .andExpect(status().isBadRequest());
        가격변경_미리보기(subscription.getId(), 31900L, null, null)
                .andExpect(status().isBadRequest());
    }

    /** 같으면 인상도 인하도 아닌 문구가 나가 시연에서 무슨 알림인지 알 수 없다. */
    @Test
    void 변경_후_금액이_현재와_같으면_400() throws Exception {
        가격변경_미리보기(subscription.getId(), 28400L, LocalDate.of(2026, 9, 1), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 가격_변경도_발송_이력을_남기지_않는다() throws Exception {
        가격변경_미리보기(subscription.getId(), 31900L, LocalDate.of(2026, 9, 1), null)
                .andExpect(status().isOk());
        가격변경_미리보기(subscription.getId(), 31900L, LocalDate.of(2026, 9, 1), null)
                .andExpect(status().isOk());

        assertThat(userNotificationRepository.findAll()).isEmpty();
    }

    @Test
    void 남의_구독으로는_발송할_수_없다_403() throws Exception {
        User other = TestSessions.createUser(userRepository, socialAccountRepository, "남", "other@example.com");
        Subscription othersSubscription = 구독(other, "Netflix", 17000L);

        미리보기(NotificationType.BILLING_REMINDER, othersSubscription.getId(), 3)
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_구독이면_404() throws Exception {
        미리보기(NotificationType.BILLING_REMINDER, 999999L, 3)
                .andExpect(status().isNotFound());
    }

    @Test
    void 지원하지_않는_알림_종류면_400() throws Exception {
        미리보기(NotificationType.SUBSCRIPTION_REVIEW, subscription.getId(), 3)
                .andExpect(status().isBadRequest());
    }

    @Test
    void daysUntil이_3도_0도_아니면_400() throws Exception {
        미리보기(NotificationType.BILLING_REMINDER, subscription.getId(), 5)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 필수값이_없으면_400() throws Exception {
        미리보기(null, subscription.getId(), 3).andExpect(status().isBadRequest());
    }

    @Test
    void 미인증이면_401() throws Exception {
        mockMvc.perform(post("/api/push-registrations/test/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NotificationType.BILLING_REMINDER, subscription.getId(), 3)))
                .andExpect(status().isUnauthorized());
    }
}
