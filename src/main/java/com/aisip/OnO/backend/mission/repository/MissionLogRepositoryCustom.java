package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.user.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 중복 방지 판정에 붙는 {@code accruedOnly} 는 "실제로 적립된 행만 셀 것인가"다.
 *
 * <p>{@code mission_log} 행은 미션을 받을 수 있는 앱에서 온 요청도 그대로 남긴다. 관리자 통계와
 * 훈장이 이 테이블만 보기 때문이다. 그런데 그 행을 중복 방지에서도 그대로 세면, 같은 계정을 구버전
 * 기기에서도 쓰는 사용자는 구버전 활동이 "이미 적립했다"로 막혀 적립으로도 진행도로도 남지 않는다(#318).
 *
 * <p>그래서 <b>자동 적립이 도는 요청은 적립된 행만 세고</b>({@code accruedOnly = true}),
 * 진행도만 올리는 요청은 지금처럼 모든 행을 센다({@code accruedOnly = false}).
 * 뒤쪽을 함께 풀면 신버전만 쓰는 사용자가 앱을 열 때마다 출석 진행도가 다시 오른다.
 */
public interface MissionLogRepositoryCustom {
    boolean alreadyWriteProblemsTodayMoreThan3(Long userId);

    boolean alreadyWriteProblemsTodayMoreThan3(Long userId, boolean accruedOnly);

    long countProblemWritesToday(Long userId);

    long countProblemWritesToday(Long userId, boolean accruedOnly);

    boolean alreadyPracticeProblem(Long problemId);

    boolean alreadyPracticeProblem(Long problemId, boolean accruedOnly);

    boolean alreadyPracticeNote(Long practiceNoteId);

    boolean alreadyPracticeNote(Long practiceNoteId, boolean accruedOnly);

    boolean alreadyLogin(Long userId);

    boolean alreadyLogin(Long userId, boolean accruedOnly);

    /**
     * 오늘 자동 적립으로 잡힌 점수 합. 하루 200점 상한이 이 값을 본다.
     *
     * <p>적립이 돌지 않은 요청의 행은 {@code point} 가 0 이라 저절로 빠진다.
     * 조건을 따로 걸지 않는 이유는 그 0 이 이미 "이 행은 적립되지 않았다"를 뜻하기 때문이다.
     */
    Long getPointSumToday(Long userId);

    Map<LocalDate, Long> getDailyActiveUsersCount(int days);

    Map<LocalDate, Long> getDailyActiveUsersCount(LocalDate startDate, LocalDate endDate);

    List<User> getActiveUsersByDate(LocalDate date);
}
