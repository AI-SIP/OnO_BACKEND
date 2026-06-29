package com.aisip.OnO.backend.studyroom.event;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;

import java.util.Map;

public record StudyRoomActivityEvent(Long userId, StudyRoomFeedEventType eventType, Map<String, Object> metadata) {
}
