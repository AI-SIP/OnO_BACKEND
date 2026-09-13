package com.aisip.OnO.backend.achievement.dto;

import java.util.List;

/**
 * {@code GET /api/achievements} 응답.
 *
 * @param achievements 열두 개 전부. 받은 것과 못 받은 것이 섞여 있다.
 *                     <b>순서는 훈장표의 순서 그대로다.</b> 앱이 이 순서를 그대로 그리고 다시 정렬하지 않는다.
 * @param newlyEarned  이번 호출에서 처음 채워진 훈장의 key 들. 앱이 "새 훈장을 받았어요" 연출에 쓴다.
 *                     없으면 빈 배열이다. 같은 사람이 연달아 부르면 두 번째부터는 비어 있다.
 */
public record AchievementListResponseDto(
        List<AchievementResponseDto> achievements,
        List<String> newlyEarned
) {
}
