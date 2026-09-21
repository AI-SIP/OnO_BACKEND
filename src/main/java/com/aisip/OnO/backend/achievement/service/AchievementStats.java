package com.aisip.OnO.backend.achievement.service;

/**
 * 한 사용자의 지금까지가 숫자로 접힌 것. 훈장 열두 개의 판정이 전부 이 값들만 본다.
 *
 * <p>판정과 세기를 갈라 두는 이유는 시험 가능성 때문이다. 조건이 맞는지 따지는 쪽
 * ({@link com.aisip.OnO.backend.achievement.entity.Achievement})에 DB 접근이 섞여 있으면
 * "오답노트 99 개와 100 개의 경계" 같은 것을 확인하려고 매번 오답노트 백 개를 만들어야 한다.
 *
 * @param maxSolveCountOnOneProblem 한 문제에 몰린 복습 횟수의 최댓값. 다섯 문제를 한 번씩 본 것과
 *                                  한 문제를 다섯 번 본 것은 다르다.
 * @param comebackCount             오답 뒤에 정답이 나온 적이 있으면 1, 없으면 0. 참/거짓을
 *                                  다른 조건과 같은 "진행도 >= 목표" 틀에 넣으려고 숫자로 둔다.
 * @param dawnSolveCount            KST 05:00 이상 08:00 미만에 한 복습 횟수.
 * @param nightSolveCount           KST 00:00 이상 03:00 미만에 한 복습 횟수.
 * @param longestCorrectStreak      복습 시각 순서로 정답이 연달아 나온 최대 길이.
 * @param longestLoginStreak        로그인이 하루도 안 끊긴 최대 일수. 오늘까지 이어질 필요는 없다.
 * @param reactionCount             리액션 세 종류(피드·공유 문제·공유 문제 댓글)를 합친 수.
 */
public record AchievementStats(
        long problemCount,
        long folderCount,
        long maxSolveCountOnOneProblem,
        long comebackCount,
        long dawnSolveCount,
        long nightSolveCount,
        long reflectionCount,
        long longestCorrectStreak,
        long longestLoginStreak,
        long studyRoomCount,
        long reactionCount
) {
}
