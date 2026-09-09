package com.aisip.OnO.backend.mission.entity;

import com.aisip.OnO.backend.mission.entity.MissionType.AbilityType;
import lombok.Getter;

/**
 * 미션이 세는 항목.
 *
 * <p>각 항목은 보상을 어느 능력치에 넣을지도 함께 정한다. 기존 {@link MissionType} 의 적립 규칙과는
 * 별개다. 미션 보상은 기존 적립 위에 얹는 보너스라 하루 200점 상한을 타지 않는다.
 */
@Getter
public enum MissionMetric {

    /** 출석. 하루에 한 번만 오른다. */
    LOGIN_DAY(AbilityType.ATTENDANCE),

    /** 오답노트 등록. 여러 장을 한 번에 등록하면 장수만큼 오른다. */
    PROBLEM_CREATED(AbilityType.NOTE_WRITE),

    /** 복습 기록. */
    SOLVE_RECORDED(AbilityType.PROBLEM_PRACTICE),

    /** 복습 기록 중 정답. */
    SOLVE_CORRECT(AbilityType.PROBLEM_PRACTICE),

    /** 복습 세트 완료. */
    PRACTICE_NOTE_COMPLETED(AbilityType.NOTE_PRACTICE),

    /** 학습 달력 기분 저장. 하루에 한 번만 오른다. */
    MOOD_LOGGED(AbilityType.ATTENDANCE);

    private final AbilityType abilityType;

    MissionMetric(AbilityType abilityType) {
        this.abilityType = abilityType;
    }
}
