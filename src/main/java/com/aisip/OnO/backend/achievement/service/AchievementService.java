package com.aisip.OnO.backend.achievement.service;

import com.aisip.OnO.backend.achievement.dto.AchievementListResponseDto;
import com.aisip.OnO.backend.achievement.dto.AchievementResponseDto;
import com.aisip.OnO.backend.achievement.entity.Achievement;
import com.aisip.OnO.backend.achievement.entity.UserAchievement;
import com.aisip.OnO.backend.achievement.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 훈장 조회와 판정.
 *
 * <p><b>판정은 조회할 때 한다.</b> 미션처럼 행동이 일어날 때마다 따지는 방법도 있지만, 그러면 열두
 * 조건이 적립 경로 네 군데에 흩어지고 무엇보다 <b>이 기능을 붙이기 전에 이미 오답노트를 백 개 적은
 * 사람이 아무것도 못 받는다.</b> 지금까지 쌓인 데이터를 그 자리에서 세면 소급이 저절로 된다.
 * 화면을 열 때 한 번 도는 비용이고, 몇 번 나가는지는 {@link AchievementStatsCollector} 주석에 있다.
 *
 * <p><b>한 번 받은 것은 취소하지 않는다.</b> 조건을 다시 계산했을 때 안 맞아도 이미 받은 것은 그대로
 * 둔다. 오답노트를 지웠다고 기록광을 뺏으면 지우는 것이 무서워진다. {@code user_achievement} 에
 * 행이 있으면 그걸로 끝이라, 아래 어디에도 지우는 경로가 없다.
 *
 * <p>엔티티를 고쳐 더티 체킹에 맡기지 않고 리포지토리의 네이티브 upsert 만 쓴다. 멱등성의 근거가
 * {@code (user_id, achievement_key)} 기본키인데, 읽고 나서 쓰는 방식으로 바꾸면 그 보장이 사라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    /**
     * 받은 시각의 기준 시간대.
     *
     * <p>운영과 테스트 모두 {@code -Duser.timezone=Asia/Seoul} 이 걸려 있어 인자 없는
     * {@code LocalDateTime.now()} 로도 같은 값이 나온다. 그래도 명시한다. 기본 시간대에 기대면
     * 배포 환경이 바뀌는 날 받은 시각이 아홉 시간 어긋나고, 그건 아무도 안 보는 사이에 일어난다.
     * {@code MissionPeriodKey} 가 같은 이유로 KST 를 못박고 있다.
     */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AchievementStatsCollector statsCollector;
    private final UserAchievementRepository userAchievementRepository;

    /**
     * 훈장 열두 개 전부와, 이번 호출에서 새로 채운 것.
     *
     * <p>읽기 전용이 아니다. 새로 채운 훈장을 이 자리에서 적는다. 적어 두지 않으면 조건이 되는 데이터를
     * 지운 순간 받은 적 없는 것이 되고, "받은 날짜"를 말할 수도 없다.
     *
     * <p>같은 사람이 연달아 두 번 불러도 두 번째의 {@code newlyEarned} 는 비어 있다. 두 번째 호출은
     * 첫 호출이 적어 둔 행을 읽고 시작하기 때문이다. 동시에 두 번 들어오면 두 응답이 같은 훈장을
     * 한 번씩 새것이라 말할 수 있지만, 그때도 행은 하나다. 최악이 기기 두 대에서 축하 연출이
     * 한 번씩 뜨는 것이라 DB 를 잠가 가며 막을 값어치가 없다고 봤다.
     */
    @Transactional
    public AchievementListResponseDto getAchievements(Long userId) {
        AchievementStats stats = statsCollector.collect(userId);
        Map<Achievement, LocalDateTime> earnedAtByAchievement = earnedAtByAchievement(userId);

        // 초 아래를 버린다. 훈장을 받은 시각에 마이크로초가 필요할 일이 없고, 계약 문서의 예시도 초 단위다.
        LocalDateTime now = LocalDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS);

        List<String> newlyEarned = new ArrayList<>();
        // values() 의 순서가 곧 훈장표의 순서다. 여기서 다시 정렬하지 않는다.
        for (Achievement achievement : Achievement.values()) {
            if (earnedAtByAchievement.containsKey(achievement) || !achievement.isSatisfiedBy(stats)) {
                continue;
            }
            userAchievementRepository.insertIfAbsent(userId, achievement.getKey(), now);
            earnedAtByAchievement.put(achievement, now);
            newlyEarned.add(achievement.getKey());
        }

        if (!newlyEarned.isEmpty()) {
            log.info("userId: {} earned achievements: {}", userId, newlyEarned);
        }

        List<AchievementResponseDto> achievements = Arrays.stream(Achievement.values())
                .map(achievement -> AchievementResponseDto.of(
                        achievement, stats, earnedAtByAchievement.get(achievement)))
                .toList();

        return new AchievementListResponseDto(achievements, newlyEarned);
    }

    /**
     * 이미 받은 훈장과 그 시각.
     *
     * <p>모르는 키가 든 행은 그냥 넘긴다. 훈장을 빼는 배포를 하면 예전 행의 키가 enum 에 없는데,
     * 거기서 예외를 던지면 훈장 하나 때문에 훈장 화면 전체가 안 열린다.
     */
    private Map<Achievement, LocalDateTime> earnedAtByAchievement(Long userId) {
        Map<Achievement, LocalDateTime> earned = new EnumMap<>(Achievement.class);
        for (UserAchievement row : userAchievementRepository.findAllByUserId(userId)) {
            Achievement.fromKey(row.getAchievementKey())
                    .ifPresent(achievement -> earned.put(achievement, row.getEarnedAt()));
        }
        return earned;
    }
}
