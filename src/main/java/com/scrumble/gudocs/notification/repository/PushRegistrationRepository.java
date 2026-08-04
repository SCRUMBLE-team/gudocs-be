package com.scrumble.gudocs.notification.repository;

import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PushRegistrationRepository extends JpaRepository<PushRegistration, Long> {

    Optional<PushRegistration> findByFid(String fid);

    List<PushRegistration> findByUserIdAndEnabledTrue(Long userId);

    /** 활성 기기를 가진(=푸시 도달 가능한) 유저 id 목록. 검사 유도 배치 대상. */
    @Query("SELECT DISTINCT p.user.id FROM PushRegistration p WHERE p.enabled = true")
    List<Long> findDistinctUserIdsWithEnabledRegistration();

    void deleteAllByUser(User user);
}
