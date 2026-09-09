package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionProgressRepository extends JpaRepository<MissionProgress, Long> {

    List<MissionProgress> findAllByUserIdAndPeriodKeyIn(Long userId, Collection<String> periodKeys);

    Optional<MissionProgress> findByUserIdAndMissionIdAndPeriodKey(Long userId, Long missionId, String periodKey);

    /**
     * 진행도를 한 문장으로 만들거나 올린다.
     *
     * <p>"없으면 만들고 있으면 올린다"를 애플리케이션에서 갈라 쓰면 두 가지가 무너진다.
     * 첫째, 같은 사용자의 요청이 겹치면 둘 다 "없다"를 읽고 INSERT 해 유니크 제약에 걸린다.
     * JPA 에서 제약 위반은 트랜잭션을 rollback-only 로 만들기 때문에 잡아서 UPDATE 로 넘어갈 수도 없다.
     * 둘째, 읽고 나서 쓰는 증가는 증가분을 잃는다.
     *
     * <p>{@code INSERT ... ON DUPLICATE KEY UPDATE} 는 둘 다 한 번에 해결한다. 중복 키를 만나면
     * MySQL 이 그 행에 배타 잠금을 걸고 UPDATE 로 바꿔 실행하므로, 예외 경로도 없고
     * 잠금 승격으로 인한 교착도 없다.
     *
     * <p>증가분은 {@code LEAST} 로 {@code target_snapshot} 에서 멈춘다. 목표를 넘겨도
     * 화면에 "4 / 3" 이 보이면 안 된다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO mission_progress
                (user_id, mission_id, period_key, current_value, target_snapshot, created_at, updated_at)
            VALUES
                (:userId, :missionId, :periodKey, LEAST(:amount, :target), :target, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
                current_value = LEAST(current_value + :amount, target_snapshot),
                updated_at = NOW(6)
            """, nativeQuery = true)
    int increaseValue(
            @Param("userId") Long userId,
            @Param("missionId") Long missionId,
            @Param("periodKey") String periodKey,
            @Param("amount") int amount,
            @Param("target") int target
    );

    /**
     * 완료 도장을 한 번만 찍는다.
     *
     * <p>{@code completed_at IS NULL} 을 조건에 넣어, 목표를 넘긴 뒤 또 올려도 완료 시각이
     * 뒤로 밀리지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE mission_progress
            SET completed_at = NOW(6),
                updated_at = NOW(6)
            WHERE user_id = :userId
              AND mission_id = :missionId
              AND period_key = :periodKey
              AND completed_at IS NULL
              AND current_value >= target_snapshot
            """, nativeQuery = true)
    int markCompleted(
            @Param("userId") Long userId,
            @Param("missionId") Long missionId,
            @Param("periodKey") String periodKey
    );

    /**
     * 보상 수령 도장. 갱신된 행이 1일 때만 지급한다.
     *
     * <p>조회로 "아직 안 받았다"를 확인하고 지급하면, 버튼을 두 번 빠르게 누른 두 요청이
     * 모두 통과해 XP 가 두 번 들어간다. {@code claimed_at IS NULL} 을 UPDATE 조건에 넣으면
     * 뒤에 온 요청은 0행을 갱신하고 거절된다.
     *
     * <p>{@code user_id} 조건은 호출부의 소유권 검사와 중복이지만 일부러 남겨 둔다.
     * 검사를 서비스에만 두면 이 메서드를 다른 곳에서 부르는 순간 소유권 검증이 통째로 빠진다.
     * "모든 데이터 접근은 userId 기준"이라는 불변식은 쿼리 자체에 박혀 있어야 한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE mission_progress
            SET claimed_at = NOW(6),
                updated_at = NOW(6)
            WHERE id = :progressId
              AND user_id = :userId
              AND claimed_at IS NULL
            """, nativeQuery = true)
    int markClaimed(@Param("progressId") Long progressId, @Param("userId") Long userId);
}
