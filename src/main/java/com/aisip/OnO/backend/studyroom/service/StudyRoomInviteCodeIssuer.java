package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.InviteCodeResponse;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomInviteCode;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomInviteCodeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudyRoomInviteCodeIssuer {

    private static final int INVITE_CODE_BOUND = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudyRoomRepository roomRepository;
    private final StudyRoomInviteCodeRepository inviteCodeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InviteCodeResponse createOrReuse(Long roomId, LocalDateTime now) {
        Optional<StudyRoomInviteCode> existing = inviteCodeRepository.findTopByRoomIdOrderByExpiredAtDesc(roomId)
                .filter(code -> !code.isExpired(now));
        if (existing.isPresent()) {
            StudyRoomInviteCode code = existing.get();
            return new InviteCodeResponse(code.getCode(), code.getExpiredAt());
        }

        inviteCodeRepository.deleteExpiredBefore(now.minusDays(7));
        StudyRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
        String code;
        do {
            code = "%06d".formatted(RANDOM.nextInt(INVITE_CODE_BOUND));
        } while (inviteCodeRepository.existsByCode(code));

        StudyRoomInviteCode inviteCode = inviteCodeRepository.saveAndFlush(
                StudyRoomInviteCode.create(room, code, now.plusHours(24)));
        return new InviteCodeResponse(inviteCode.getCode(), inviteCode.getExpiredAt());
    }
}
