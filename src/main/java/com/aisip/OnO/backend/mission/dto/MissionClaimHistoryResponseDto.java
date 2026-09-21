package com.aisip.OnO.backend.mission.dto;

import java.util.List;

/**
 * 보상 획득 기록 페이지.
 *
 * <p>{@code content}/{@code nextCursor}/{@code hasNext}/{@code size} 는
 * {@code CursorPageResponse} 와 이름과 의미가 같다. 프론트가 같은 방식으로 파싱한다.
 * 합계 두 개를 더 실어야 해서 레코드를 따로 뒀을 뿐이다.
 *
 * <p>합계는 <b>첫 페이지에만</b> 채운다. 페이지를 넘길 때마다 전체를 다시 세는 것은 낭비이고,
 * 화면 맨 위의 "지금까지 받은 XP" 는 첫 페이지 값만 쓴다. 이후 페이지에서는 두 값이 null 이다.
 */
public record MissionClaimHistoryResponseDto(
        List<MissionClaimHistoryItemDto> content,
        Long nextCursor,
        boolean hasNext,
        int size,
        Long totalClaimedXp,
        Long totalClaimedCount
) {

    public static MissionClaimHistoryResponseDto firstPage(
            List<MissionClaimHistoryItemDto> content, Long nextCursor, boolean hasNext, int size,
            long totalClaimedXp, long totalClaimedCount
    ) {
        return new MissionClaimHistoryResponseDto(
                content, nextCursor, hasNext, size, totalClaimedXp, totalClaimedCount);
    }

    public static MissionClaimHistoryResponseDto nextPage(
            List<MissionClaimHistoryItemDto> content, Long nextCursor, boolean hasNext, int size
    ) {
        return new MissionClaimHistoryResponseDto(content, nextCursor, hasNext, size, null, null);
    }
}
