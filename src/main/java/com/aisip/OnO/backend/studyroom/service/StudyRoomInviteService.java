package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomInviteCode;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomInviteCodeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StudyRoomInviteService {

    private static final int INVITE_CODE_RETRY_COUNT = 5;

    private final StudyRoomAccessService accessService;
    private final StudyRoomInviteCodeRepository inviteCodeRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StudyRoomService studyRoomService;
    private final StudyRoomInviteCodeIssuer inviteCodeIssuer;

    public InviteCodeResponse issueInviteCode(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        LocalDateTime now = LocalDateTime.now();
        return inviteCodeRepository.findTopByRoomIdOrderByExpiredAtDesc(roomId)
                .filter(code -> !code.isExpired(now))
                .map(code -> new InviteCodeResponse(code.getCode(), code.getExpiredAt()))
                .orElseGet(() -> createInviteCode(roomId, now));
    }

    @Transactional
    public StudyRoomDetailResponse join(StudyRoomJoinRequest request, Long userId) {
        if (request.code() == null || !request.code().matches("\\d{6}")) {
            throw new ApplicationException(StudyRoomErrorCase.INVITE_CODE_INVALID);
        }
        StudyRoomInviteCode inviteCode = inviteCodeRepository.findByCode(request.code())
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.INVITE_CODE_INVALID));
        if (inviteCode.isExpired(LocalDateTime.now())) {
            throw new ApplicationException(StudyRoomErrorCase.INVITE_CODE_EXPIRED);
        }
        StudyRoom room = studyRoomService.lockRoom(inviteCode.getRoom().getId());
        if (memberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new ApplicationException(StudyRoomErrorCase.ALREADY_MEMBER);
        }
        if (memberRepository.countByRoomId(room.getId()) >= StudyRoomService.MAX_ROOM_MEMBER_COUNT) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_FULL);
        }
        if (memberRepository.countByUserId(userId) >= StudyRoomService.MAX_USER_ROOM_COUNT) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_LIMIT_EXCEEDED);
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        room.addMember(StudyRoomMember.create(user, StudyRoomMemberRole.MEMBER));
        return studyRoomService.buildDetail(room);
    }

    private InviteCodeResponse createInviteCode(Long roomId, LocalDateTime now) {
        for (int attempt = 0; attempt < INVITE_CODE_RETRY_COUNT; attempt++) {
            try {
                return inviteCodeIssuer.createOrReuse(roomId, now);
            } catch (DataIntegrityViolationException ignored) {
                if (attempt == INVITE_CODE_RETRY_COUNT - 1) {
                    throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
                }
            }
        }
        throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
    }
}
