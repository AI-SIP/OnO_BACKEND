package com.aisip.OnO.backend.cosmetic.entity;

import com.aisip.OnO.backend.mission.entity.MissionType.AbilityType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * 해금 판정에 쓰는 사용자의 레벨 묶음.
 *
 * <p>보유 여부가 아이템마다 다른 레벨을 보게 되면서 생겼다. 아이템에 {@code required_ability} 가
 * 적혀 있으면 그 능력치 레벨을, 비어 있으면 총 학습 레벨을 본다. 판정을 하는 곳마다
 * "이 아이템은 어느 레벨과 비교해야 하지" 를 다시 따지면 한 군데만 빠뜨려도
 * 열려야 할 것이 안 열리거나 그 반대가 된다. 그 갈림을 {@link #levelFor(AbilityType)} 한 곳에 모은다.
 *
 * <p>레벨 값을 스냅샷으로 들고 다닌다. 조회 한 번 안에서 같은 사용자의 레벨이 달라 보이면
 * 목록의 {@code owned} 와 프리셋 계산이 서로 어긋날 수 있다.
 */
public record CosmeticUnlockLevels(long totalStudyLevel, Map<AbilityType, Long> abilityLevels) {

    /** 미션 상태가 아직 없는 사용자의 레벨. {@code StudyRoomMapper} 와 같은 기준이다. */
    public static final long DEFAULT_LEVEL = 1L;

    public CosmeticUnlockLevels {
        abilityLevels = abilityLevels == null
                ? Map.of()
                : Map.copyOf(abilityLevels);
    }

    /** 미션 상태가 없는 사용자. 모든 레벨이 1 이라 아무것도 열려 있지 않다. */
    public static CosmeticUnlockLevels defaults() {
        return new CosmeticUnlockLevels(DEFAULT_LEVEL, Map.of());
    }

    public static CosmeticUnlockLevels from(UserMissionStatus status) {
        if (status == null) {
            return defaults();
        }

        Map<AbilityType, Long> levels = new EnumMap<>(AbilityType.class);
        levels.put(AbilityType.ATTENDANCE, orDefault(status.getAttendanceLevel()));
        levels.put(AbilityType.NOTE_WRITE, orDefault(status.getNoteWriteLevel()));
        levels.put(AbilityType.PROBLEM_PRACTICE, orDefault(status.getProblemPracticeLevel()));
        levels.put(AbilityType.NOTE_PRACTICE, orDefault(status.getNotePracticeLevel()));

        return new CosmeticUnlockLevels(orDefault(status.getTotalStudyLevel()), levels);
    }

    /**
     * 이 아이템과 비교할 레벨.
     *
     * <p>{@code ability} 가 null 이면 총 학습 레벨이다. 0 이나 -1 같은 마법값을 쓰지 않는 것과
     * 같은 이유로, "능력치 조건이 없다" 는 것을 null 하나로만 표현한다.
     */
    public long levelFor(AbilityType ability) {
        if (ability == null) {
            return totalStudyLevel;
        }
        return abilityLevels.getOrDefault(ability, DEFAULT_LEVEL);
    }

    private static long orDefault(Long level) {
        return level == null ? DEFAULT_LEVEL : level;
    }
}
