package com.scrumble.gudocs.notification.service;

import com.scrumble.gudocs.common.TestSessions;
import com.scrumble.gudocs.notification.dto.request.PushRegistrationRequest;
import com.scrumble.gudocs.notification.entity.PushPlatform;
import com.scrumble.gudocs.notification.repository.PushRegistrationRepository;
import com.scrumble.gudocs.users.entity.User;
import com.scrumble.gudocs.users.repository.SocialAccountRepository;
import com.scrumble.gudocs.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트랜잭션이 없는 동시 등록 테스트. 실제 커밋/스레드 가시성이 필요하므로 @Transactional을 쓰지 않고,
 * 남은 데이터는 @AfterEach에서 직접 정리한다.
 */
@SpringBootTest
class PushRegistrationConcurrencyTest {

    private static final String FID = "fid-concurrent";

    @Autowired
    private PushRegistrationService pushRegistrationService;
    @Autowired
    private PushRegistrationRepository pushRegistrationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SocialAccountRepository socialAccountRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<User> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 비트랜잭션 테스트라 커밋된 데이터를 트랜잭션 안에서 직접 정리한다 (FK 순서: 자식 → users)
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            pushRegistrationRepository.findByFid(FID).ifPresent(pushRegistrationRepository::delete);
            for (User user : createdUsers) {
                socialAccountRepository.deleteAllByUser(user);
                userRepository.deleteById(user.getId());
            }
        });
    }

    private User newUser(String email) {
        User user = TestSessions.createUser(userRepository, socialAccountRepository, "동시", email);
        createdUsers.add(user);
        return user;
    }

    @Test
    void 동일_FID_동시_등록시_500없이_행_하나만_유지() throws Exception {
        User userA = newUser("concurrent-a@example.com");
        User userB = newUser("concurrent-b@example.com");
        List<Long> requesters = List.of(userA.getId(), userB.getId());

        int threads = requesters.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (Long uid : requesters) {
            pool.submit(() -> {
                try {
                    start.await();
                    pushRegistrationService.register(uid,
                            new PushRegistrationRequest(FID, PushPlatform.WEB, "device"));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown(); // 두 스레드 동시 출발
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 500 유발 예외 없음
        assertThat(errors).isEmpty();
        // 같은 fid 행은 정확히 하나
        long rows = pushRegistrationRepository.findAll().stream()
                .filter(r -> FID.equals(r.getFid())).count();
        assertThat(rows).isEqualTo(1);
        // 소유자는 두 요청자 중 하나로 확정
        Long ownerId = pushRegistrationRepository.findByFid(FID).orElseThrow().getUser().getId();
        assertThat(ownerId).isIn(userA.getId(), userB.getId());
    }
}
