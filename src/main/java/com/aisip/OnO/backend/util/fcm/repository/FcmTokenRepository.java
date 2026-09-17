package com.aisip.OnO.backend.util.fcm.repository;

import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    boolean existsByToken(String token);

    boolean existsByUserIdAndToken(Long userId, String token);

    Optional<FcmToken> findByToken(String token);

    List<FcmToken> findAllByUserId(Long userId);

    /** 같은 기기 토큰이 다른 사용자에게 묶여 있는 행. 계정 전환 시 지울 대상이다. */
    List<FcmToken> findAllByTokenAndUserIdNot(String token, Long userId);

    /**
     * 탈퇴한 사용자의 기기 토큰을 지운다. (user_id, token) 유니크 인덱스의 선두 컬럼을 탄다.
     * 영속성 컨텍스트를 비우지 않는다. 탈퇴 경로가 아직 사용자 엔티티를 수정 중이라 비우면 그 변경이 사라진다.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM FcmToken f WHERE f.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Transactional
    void deleteByToken(String token);
}
