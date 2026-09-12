package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.entity.MissionRewardType;
import com.aisip.OnO.backend.mission.entity.MissionType.AbilityType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미션 보상 지급.
 *
 * <p><b>미션 보상은 하루 200점 상한을 타지 않는다.</b> 상한은 {@code MissionLogService} 의 자동 적립에만
 * 있는 규칙이고, 미션 보상은 그 위에 얹는 별도 경로다. 상한 역할은 미션 설계가 대신한다.
 * 일일 6종을 다 받아도 75, 주간 4종을 다 받아도 360 이다.
 *
 * <p>자동 적립({@code ono.mission.legacy-accrual.enabled})을 끄면 여기가 XP 가 들어오는 유일한 경로가 된다.
 * 켜져 있는 동안에는 두 경로가 함께 도는데, 그건 "더 받는" 것이라 고장이 아니다.
 *
 * <p>능력치 경험치 반영은 엔티티를 읽어 고치는 방식이라 같은 사용자의 동시 지급에 취약하다.
 * 기존 적립 경로와 같은 이유로 사용자 행을 배타 잠금으로 먼저 잡아 직렬화한다.
 * 잠금을 나중에 잡으면 잠금 승격 과정에서 교착이 난다.
 */
@Component
@RequiredArgsConstructor
public class MissionRewardGranter {

    private final UserRepository userRepository;

    /**
     * 지급 전에 사용자 행을 먼저 잠근다.
     *
     * <p>잠금 순서를 <b>사용자 → 진행도</b> 하나로 통일하기 위해서다. 기존 적립 경로
     * ({@code MissionLogService.registerLoginMission})는 사용자 행을 잠근 상태에서 진행도를 올리는데,
     * 받기가 진행도를 먼저 잠그고 사용자를 나중에 잠그면 두 요청이 서로의 잠금을 기다려 교착이 난다.
     */
    @Transactional
    public void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
    }

    @Transactional
    public GrantResult grant(Long userId, MissionMetric metric, MissionRewardType rewardType, int rewardValue) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));

        UserMissionStatus status = user.getUserMissionStatus();
        long levelBefore = status.getTotalStudyLevel();

        // 어느 능력치에 넣었는지와 그 능력치의 전후 레벨을 함께 돌려준다. 치장 해금이
        // 총 학습 레벨만이 아니라 능력치 레벨로도 열리기 때문에, 호출부가 이 값을 모르면
        // 능력치가 올라 열린 것을 수령 응답에서 통째로 빠뜨린다.
        AbilityType ability = metric == null ? null : metric.getAbilityType();
        long abilityLevelBefore = abilityLevelOf(status, ability);

        if (rewardType == MissionRewardType.XP && rewardValue > 0 && ability != null) {
            long point = rewardValue;
            switch (ability) {
                case ATTENDANCE -> status.gainAttendancePoint(point);
                case NOTE_WRITE -> status.gainNoteWritePoint(point);
                case PROBLEM_PRACTICE -> status.gainProblemPracticePoint(point);
                case NOTE_PRACTICE -> status.gainNotePracticePoint(point);
            }
        }

        long levelAfter = status.getTotalStudyLevel();
        return new GrantResult(levelBefore, levelAfter, levelAfter > levelBefore,
                ability, abilityLevelBefore, abilityLevelOf(status, ability));
    }

    /** 능력치가 없는 보상이면 0 이다. 전후가 같으니 해금 구간이 비게 된다. */
    private long abilityLevelOf(UserMissionStatus status, AbilityType ability) {
        if (ability == null) {
            return 0L;
        }
        return switch (ability) {
            case ATTENDANCE -> status.getAttendanceLevel();
            case NOTE_WRITE -> status.getNoteWriteLevel();
            case PROBLEM_PRACTICE -> status.getProblemPracticeLevel();
            case NOTE_PRACTICE -> status.getNotePracticeLevel();
        };
    }

    /**
     * 지급 결과.
     *
     * <p>{@code levelBefore} 를 함께 돌려주는 이유는 호출부가 "이번에 무엇이 열렸는지" 를
     * 계산해야 하기 때문이다. 레벨이 한 번에 여러 단계 오를 수 있어 {@code leveledUp} 만으로는
     * 어디서 어디까지 올랐는지 알 수 없다.
     *
     * <p>{@code abilityType} 과 그 능력치의 전후 레벨도 함께 준다. 치장 해금이 능력치별로도
     * 열리는데, 어느 능력치에 얼마가 들어갔는지는 여기서만 알 수 있다.
     * XP 가 아닌 보상이면 {@code abilityType} 이 null 이고 전후 레벨은 둘 다 0 이라 구간이 비어 있다.
     *
     * <p>여기서 해금 아이템까지 조회하지 않는다. 이 클래스는 사용자 행을 배타 잠금으로 잡은
     * 구간이라, 잠금을 들고 하는 일을 늘리면 같은 사용자의 다른 요청이 그만큼 더 기다린다.
     */
    public record GrantResult(
            long levelBefore,
            Long totalStudyLevel,
            boolean leveledUp,
            AbilityType abilityType,
            long abilityLevelBefore,
            long abilityLevelAfter
    ) {
    }
}
