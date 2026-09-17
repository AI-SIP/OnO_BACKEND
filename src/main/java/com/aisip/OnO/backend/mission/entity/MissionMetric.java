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
    LOGIN_DAY(AbilityType.ATTENDANCE, true),

    /** 오답노트 등록. 여러 장을 한 번에 등록하면 장수만큼 오른다. */
    PROBLEM_CREATED(AbilityType.NOTE_WRITE, true),

    /** 복습 기록. */
    SOLVE_RECORDED(AbilityType.PROBLEM_PRACTICE, true),

    /** 복습 기록 중 정답. */
    SOLVE_CORRECT(AbilityType.PROBLEM_PRACTICE, true),

    /** 복습 세트 완료. */
    PRACTICE_NOTE_COMPLETED(AbilityType.NOTE_PRACTICE, true),

    /** 학습 달력 기분 저장. 하루에 한 번만 오른다. 기분 저장은 자동 적립을 부르지 않는다. */
    MOOD_LOGGED(AbilityType.ATTENDANCE, false);

    private final AbilityType abilityType;

    /**
     * 이 항목을 올리는 행동이 자동 적립({@code MissionLogService})도 함께 부르는가.
     *
     * <p>{@code true} 인 항목은 자동 적립이 도는 요청에서 진행도를 올리지 않는다. 올리면 같은 행동으로
     * 적립 XP 와 미션 보상 XP 를 둘 다 받는다. 새 항목을 추가할 때 이 값을 반드시 정해야 하도록
     * 생성자 인자로 두었다.
     */
    private final boolean alsoAccruedByLegacyPath;

    MissionMetric(AbilityType abilityType, boolean alsoAccruedByLegacyPath) {
        this.abilityType = abilityType;
        this.alsoAccruedByLegacyPath = alsoAccruedByLegacyPath;
    }
}
