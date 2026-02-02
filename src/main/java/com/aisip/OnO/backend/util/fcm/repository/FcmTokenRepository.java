package com.aisip.OnO.backend.util.fcm.repository;

import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    boolean existsByToken(String token);

    boolean existsByUserIdAndToken(Long userId, String token);

    Optional<FcmToken> findByToken(String token);

    List<FcmToken> findAllByUserId(Long userId);
}
