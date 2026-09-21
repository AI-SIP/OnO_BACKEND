package com.aisip.OnO.backend.achievement.repository;

import com.aisip.OnO.backend.achievement.entity.UserAchievement;
import com.aisip.OnO.backend.achievement.entity.UserAchievementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UserAchievementId> {

    /** 소유권: 훈장은 언제나 요청한 사용자 것만 읽는다. */
    List<UserAchievement> findAllByUserId(Long userId);

    /**
     * 아직 없을 때만 넣는다. 이미 받은 훈장은 손대지 않는다.
     *
     * <p>여기가 "한 번 받은 것은 취소하지 않는다" 와 "몇 번을 불러도 행이 하나다" 를 동시에 지킨다.
     * {@code ON DUPLICATE KEY UPDATE user_id = user_id} 는 "이미 있으면 그대로 둔다"는 뜻이라,
     * 두 번째 호출이 {@code earned_at} 을 오늘로 덮어쓰지 않는다. 덮어쓰면 사용자가 훈장 화면을
     * 열 때마다 받은 날짜가 오늘로 바뀐다.
     *
     * <p>{@code INSERT IGNORE} 를 쓰지 않는다. 그쪽은 키 충돌만이 아니라 길이 초과·타입 불일치 같은
     * 진짜 오류까지 경고로 삼켜 버린다. 훈장 키가 32 자를 넘게 되면 조용히 잘린 행이 남는 대신
     * 여기서 터져야 한다.
     *
     * <p>{@code earned_at} 을 {@code NOW(6)} 이 아니라 인자로 받는다. DB 서버의 시간대가 앱과 다르면
     * 받은 시각이 아홉 시간 어긋난 채로 앱에 뜬다. 기준 시각은 애플리케이션이 정한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO user_achievement (user_id, achievement_key, earned_at)
            VALUES (:userId, :achievementKey, :earnedAt)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("achievementKey") String achievementKey,
            @Param("earnedAt") LocalDateTime earnedAt
    );
}
