package com.aisip.OnO.backend.notice.entity;

/**
 * 공지 성격. 프론트가 팝업 색상과 아이콘을 이 값으로 고른다.
 */
public enum NoticeType {

    INFO,       // 일반 안내
    WARNING,    // 점검, 장애 같은 주의 안내
    EVENT       // 이벤트 홍보
}
