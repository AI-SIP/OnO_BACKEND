package com.aisip.OnO.backend.achievement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 받은 훈장. (userId, achievementKey) 하나당 한 행이다.
 *
 * <p><b>(user_id, achievement_key) 복합 기본키가 멱등성의 근거다.</b> 판정이 조회 때마다 도는 구조라
 * 같은 사람이 훈장 화면을 두 번 열면 같은 INSERT 가 두 번 나간다. 중복을 막는 것을 애플리케이션
 * 검사에 맡기면 두 요청이 동시에 "없다"를 읽고 둘 다 INSERT 하는 창이 열린다. DB 가 막으면 그 창이 없다.
 *
 * <p>이 엔티티는 <b>읽기 전용으로만</b> 쓴다. 쓰기는 리포지토리의 네이티브 upsert 를 탄다.
 * {@link com.aisip.OnO.backend.cosmetic.entity.UserCosmeticLoadout} 과 같은 방식이다.
 *
 * <p>키는 {@link Achievement} 의 enum 이름이 아니라 {@code archivist} 같은 <b>계약 키</b>를 담는다.
 * API 와 앱 에셋 경로가 쓰는 값과 같아야 행을 보고 무엇인지 알 수 있다.
 *
 * <p>user 에 외래키를 걸지 않는다. {@code user_cosmetic_loadout} 과 같은 이유로, INSERT 마다
 * 부모 사용자 행에 공유 잠금이 붙으면 사용자 행을 배타 잠금으로 잡는 미션 보상 지급 경로와
 * 잠금 순서가 엇갈릴 수 있다.
 */
@Entity
@Getter
@IdClass(UserAchievementId.class)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_achievement")
public class UserAchievement {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * V42 의 {@code achievement_key VARCHAR(32)} 와 맞춘다. {@code columnDefinition} 을 함께 적는 이유는
     * {@code UserCosmeticLoadout.slot} 과 같다. 길이가 테스트 스키마와 운영 스키마에서 갈리면
     * 길이 초과로 나는 오류를 테스트가 재현하지 못한다.
     */
    @Id
    @Column(name = "achievement_key", nullable = false, length = 32, columnDefinition = "varchar(32)")
    private String achievementKey;

    /** 이 훈장을 처음 채운 시각. KST 다. 근거는 AchievementService 주석에 있다. */
    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;
}
