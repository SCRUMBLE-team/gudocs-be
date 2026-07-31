package com.scrumble.gudocs.notification.repository;

import com.scrumble.gudocs.notification.entity.PushRegistration;
import com.scrumble.gudocs.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushRegistrationRepository extends JpaRepository<PushRegistration, Long> {

    Optional<PushRegistration> findByFid(String fid);

    List<PushRegistration> findByUserIdAndEnabledTrue(Long userId);

    void deleteAllByUser(User user);
}
