package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.entity.StudySession;
import com.aisip.OnO.backend.studyroom.event.StudyRoomActivityEvent;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudySessionRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudySessionService {

    private final StudyRoomAccessService accessService;
    private final StudySessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public ActiveStudySessionsResponse getActiveSessions(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        return new ActiveStudySessionsResponse(sessionRepository.findActiveByRoomId(roomId).stream()
                .map(session -> new ActiveStudySessionResponse(
                        session.getUser().getId(), session.getUser().getName(), session.getStartedAt()))
                .toList());
    }

    @Transactional
    public StudySessionStartResponse start(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        if (!sessionRepository.findActiveByUserIdForUpdate(userId).isEmpty()) {
            throw new ApplicationException(StudyRoomErrorCase.SESSION_ALREADY_ACTIVE);
        }
        StudyRoom room = accessService.getRoomOrThrow(roomId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        StudySession session = sessionRepository.save(StudySession.start(room, user, LocalDateTime.now()));
        eventPublisher.publishEvent(new StudyRoomActivityEvent(userId, StudyRoomFeedEventType.SESSION_STARTED, Map.of()));
        return new StudySessionStartResponse(session.getId(), session.getStartedAt());
    }

    @Transactional
    public StudySessionEndResponse end(Long roomId, Long sessionId, Long userId) {
        accessService.validateMember(roomId, userId);
        StudySession session = sessionRepository.findByIdAndRoomIdAndUserId(sessionId, roomId, userId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.SESSION_NOT_FOUND));
        if (session.getEndedAt() == null) {
            session.end(LocalDateTime.now());
        }
        return new StudySessionEndResponse(session.getId(), session.getStartedAt(), session.getEndedAt(), session.getDurationMinutes());
    }

    @Transactional
    public void closeExpiredSessions(LocalDateTime threshold, LocalDateTime endedAt) {
        sessionRepository.findAllByEndedAtIsNullAndStartedAtBefore(threshold)
                .forEach(session -> session.end(endedAt));
    }
}
