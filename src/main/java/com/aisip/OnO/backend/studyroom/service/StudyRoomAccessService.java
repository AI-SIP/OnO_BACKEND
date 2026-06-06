package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRoomAccessService {

    private final StudyRoomRepository roomRepository;
    private final StudyRoomMemberRepository memberRepository;

    public StudyRoom getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
    }

    public StudyRoomMember getMemberOrThrow(Long roomId, Long userId) {
        getRoomOrThrow(roomId);
        return memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_FORBIDDEN));
    }

    public void validateMember(Long roomId, Long userId) {
        getMemberOrThrow(roomId, userId);
    }

    public void validateHost(Long roomId, Long userId) {
        StudyRoomMember member = getMemberOrThrow(roomId, userId);
        if (member.getRole() != StudyRoomMemberRole.HOST) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_HOST_ONLY);
        }
    }
}
