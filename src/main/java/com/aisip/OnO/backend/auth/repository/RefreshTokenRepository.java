package com.aisip.OnO.backend.auth.repository;

import com.aisip.OnO.backend.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByUserId(Long userId);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    /**
     * 탈퇴한 사용자의 세션을 기기 수와 상관없이 한 번에 지운다. idx_refresh_token_user_id 를 탄다.
     * 영속성 컨텍스트를 비우지 않는다. 탈퇴 경로가 아직 사용자 엔티티를 수정 중이라 비우면 그 변경이 사라진다.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    void deleteByRefreshToken(String refreshToken);
}
