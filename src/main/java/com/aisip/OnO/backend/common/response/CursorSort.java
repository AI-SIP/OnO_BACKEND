package com.aisip.OnO.backend.common.response;

/**
 * 커서 목록을 어느 순서로 줄지.
 *
 * 커서는 언제나 id 라 등록 순서만 고를 수 있다. 기본은 예전처럼 오래된 순이다.
 * 이미 깔린 앱은 정렬 값을 보내지 않으니 순서가 바뀌면 안 된다.
 */
public enum CursorSort {
    /** 먼저 만든 것이 위. id 오름차순. */
    OLDEST,
    /** 최근에 만든 것이 위. id 내림차순. */
    NEWEST;

    public boolean isNewest() {
        return this == NEWEST;
    }
}
