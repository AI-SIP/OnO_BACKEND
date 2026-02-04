package com.aisip.OnO.backend.common.response;

import java.util.List;

/**
 * 커서 기반 페이징 응답 DTO
 * @param <T> 응답 데이터 타입
 */
public record CursorPageResponse<T>(
        List<T> content,        // 조회된 데이터 리스트
        Long nextCursor,        // 다음 페이지 커서 (null이면 마지막 페이지)
        boolean hasNext,        // 다음 페이지 존재 여부
        int size                // 요청한 페이지 크기
) {
    /**
     * 커서 페이지 응답 생성
     * @param content 조회된 데이터
     * @param nextCursor 다음 커서
     * @param hasNext 다음 페이지 존재 여부
     * @param size 페이지 크기
     */
    public static <T> CursorPageResponse<T> of(List<T> content, Long nextCursor, boolean hasNext, int size) {
        return new CursorPageResponse<>(content, nextCursor, hasNext, size);
    }

    /**
     * 마지막 페이지 응답 생성
     * @param content 조회된 데이터
     * @param size 페이지 크기
     */
    public static <T> CursorPageResponse<T> last(List<T> content, int size) {
        return new CursorPageResponse<>(content, null, false, size);
    }
}